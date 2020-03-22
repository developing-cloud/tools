package cloud.developing.iac.cicd;

import static cloud.developing.iac.cicd.Util.envTypes;
import static cloud.developing.iac.common.CdkMap.map;
import static java.util.Arrays.asList;
import static software.amazon.awscdk.services.codebuild.LinuxBuildImage.STANDARD_3_0;
import static software.amazon.awscdk.services.codecommit.Repository.fromRepositoryName;
import static software.amazon.awscdk.services.codepipeline.StageProps.builder;
import static software.amazon.awscdk.services.codepipeline.actions.CodeCommitTrigger.EVENTS;
import static software.amazon.awscdk.services.iam.Role.fromRoleArn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.BuildSpec;
import software.amazon.awscdk.services.codebuild.IProject;
import software.amazon.awscdk.services.codebuild.PipelineProject;
import software.amazon.awscdk.services.codecommit.IRepository;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.StageProps;
import software.amazon.awscdk.services.codepipeline.actions.CloudFormationCreateReplaceChangeSetAction;
import software.amazon.awscdk.services.codepipeline.actions.CloudFormationExecuteChangeSetAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeCommitSourceAction;
import software.amazon.awscdk.services.codepipeline.actions.ManualApprovalAction;
import software.amazon.awscdk.services.iam.CompositePrincipal;
import software.amazon.awscdk.services.iam.FromRoleArnOptions;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.RoleProps;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.kms.IKey;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketAttributes;

public class CicdStack extends Stack {

	private final class BuildConf {

		private final String envType, cicdBucket;

		private final IRole role;

		private BuildConf(String envType, String cicdBucket, IRole role) {
			this.envType = envType;
			this.cicdBucket = cicdBucket;
			this.role = role;
		}

	}

	private final Map<String, Function<BuildConf, IProject>> buildProjectMap = new HashMap<>();
	{
		buildProjectMap.put("java", this::javaCodeBuild);
		buildProjectMap.put("python", this::pythonCodeBuild);
		buildProjectMap.put("amazon-states-language", this::amazonStatesCodeBuild);
	}

	private final String appName, outputTemplate;;

	private final Properties props;

	public CicdStack(Construct scope, String appName, Properties props) {
		super(scope, appName);
		this.appName = appName;
		this.props = props;
		this.outputTemplate = props.getProperty("output-template", "outputTemplate.yaml");

		var code = fromRepositoryName(this, "repo", props.getProperty("repo-name"));
		var deploymentRoleName = "deployment-" + appName + "-role";
		var deploymentRoleProps = RoleProps.builder().roleName(deploymentRoleName)
		        .assumedBy(new CompositePrincipal(new ServicePrincipal("codebuild.amazonaws.com"),
		                new ServicePrincipal("codepipeline.amazonaws.com")))
		        .build();

		var deploymentRole = new Role(this, deploymentRoleName, deploymentRoleProps);
		envTypes(appName).forEach(envType -> buildCicdForEnvType(envType, code, deploymentRole));
	}

	private void buildCicdForEnvType(String envType, IRepository code, IRole deploymentRole) {
		var sourceOutput = new Artifact("SourceOutput");
		var buildOutput = new Artifact("BuildOutput");

		var cicdBucket = props.getProperty("cicd-bucket") + getRegion();

		var deploymentAccount = props.getProperty(envType + ".account");

		var appLanguage = props.getProperty("app-language");

		var bc = new BuildConf(envType, cicdBucket, deploymentRole);

		IProject buildProject = buildProjectMap.get(appLanguage).apply(bc);

		var targetDeploymentRole = fromRoleName(name("target-deployment-role", envType), "deployment-role",
		        deploymentAccount);

		var stackName = appName + "-" + envType;
		var changeSetName = "ChangeSet";

		var bucketAttributes = BucketAttributes.builder().bucketName(cicdBucket)
		        .encryptionKey(fromKeyName(id("cicd-bucket-key", envType), props.getProperty("cicd-bucket-key")))
		        .build();

		var source = builder().stageName("Source")
		        .actions(List.of(CodeCommitSourceAction.Builder.create().actionName("Source").role(deploymentRole)
		                .repository(code).output(sourceOutput).branch(envType).trigger(EVENTS).build()))
		        .build();

		var build = builder().stageName("Build").actions(List.of(CodeBuildAction.Builder.create().actionName("Build")
		        .role(deploymentRole).project(buildProject).input(sourceOutput).outputs(List.of(buildOutput)).build()))
		        .build();

		var approval = builder()
		        .stageName("Approval").actions(List.of(ManualApprovalAction.Builder.create().actionName("Approval")
		                .role(deploymentRole).notifyEmails(List.of(props.getProperty("cicd-notify-emails"))).build()))
		        .build();

		var deploy = builder().stageName("Deploy").actions(List.of(
		        CloudFormationCreateReplaceChangeSetAction.Builder.create().runOrder(1).actionName("CreateChangeSet")
		                .role(targetDeploymentRole).changeSetName(changeSetName).deploymentRole(targetDeploymentRole)
		                .stackName(stackName).parameterOverrides(map().with("envType", envType))
		                .templatePath(buildOutput.atPath(outputTemplate)).adminPermissions(true)
		                .extraInputs(List.of(buildOutput)).build(),
		        CloudFormationExecuteChangeSetAction.Builder.create().runOrder(2).actionName("ExecuteChangeSet")
		                .role(targetDeploymentRole).changeSetName(changeSetName).stackName(stackName).build()

		)).build();

		final List<StageProps> stages = envType.startsWith("prod") ? List.of(source, build, approval, deploy)
		        : List.of(source, build, deploy);

		Pipeline.Builder.create(this, id("cicd", envType)).pipelineName(name("pipeline", envType))
		        .artifactBucket(Bucket.fromBucketAttributes(this, id("cicd-bucket", envType), bucketAttributes))
		        .role(deploymentRole).stages(stages).build();
	}

	private IProject amazonStatesCodeBuild(BuildConf bc) {
		var envType = bc.envType;
		var role = bc.role;

		var buildProject = PipelineProject.Builder.create(this, id("cdk-build", envType)).role(role)
		        .projectName(name("cdk-build", envType)).buildSpec(BuildSpec.fromObject(map().
				// @formatter:off
					with("version", "0.2")
					.with("phases", map().
						with("install", map().
							with("commands", List.of("npm install -g aws-cdk", "npm init -y"))).
						with("build", map().
						    with("commands", List.of("mvn compile", "cdk synth --require-approval=never --app \"mvn exec:java -Dexec.mainClassmvn exec:java -Dexec.mainClass=" + props.getProperty("java-cdk-class") + " -Dexec.args=" + envType +"\"")))).
					with("artifacts", map().
							with("base-directory", "cdk.out").
							with("files", List.of("**/*"))).
					with("cache", map().
							with("paths", "/root/.m2/**/*"))
		        // @formatter:on
				)).environment(BuildEnvironment.builder().buildImage(STANDARD_3_0).build()).build();
		return buildProject;
	}

	private IProject javaCodeBuild(BuildConf bc) {
		var envType = bc.envType;
		var cicdBucket = bc.cicdBucket;
		var role = bc.role;

		var buildProject = PipelineProject.Builder.create(this, id("lambda-build", envType)).role(role)
		        .projectName(name("lambda-build", envType)).buildSpec(BuildSpec.fromObject(map().
				// @formatter:off
					with("version", "0.2")
					.with("phases", map().
						with("build", map().
								with("commands", List.of("gradle buildZip"))).
						with("post_build", map().
								with("commands", List.of("aws cloudformation package --region $AWS_REGION --template-file template.yaml --s3-bucket " + cicdBucket +" --output-template-file outputTemplate.yaml --s3-prefix sam"))
						)).
					with("artifacts", map().
							with("type", "zip").
							with("files", List.of(outputTemplate))).
					with("cache", map().
							with("paths", "/root/.m2/**/*"))
		        // @formatter:on
				)).environment(BuildEnvironment.builder().buildImage(STANDARD_3_0).build()).build();
		return buildProject;
	}

	private IProject pythonCodeBuild(BuildConf bc) {
		var envType = bc.envType;
		var cicdBucket = bc.cicdBucket;
		var role = bc.role;

		var buildProject = PipelineProject.Builder.create(this, id("lambda-build", envType)).role(role)
		        .projectName(name("lambda-build", envType)).buildSpec(BuildSpec.fromObject(map().
				// @formatter:off
					with("version", "0.2")
					.with("phases", map().
						with("build", map().
								with("commands", asList(props.getProperty("commands", "").split(";")))).
						with("post_build", map().
								with("commands", List.of("aws cloudformation package --region $AWS_REGION --template-file template.yaml --s3-bucket " + cicdBucket +" --output-template-file outputTemplate.yaml --s3-prefix sam"))
						)).
					with("artifacts", map().
							with("type", "zip").
							with("files", List.of(outputTemplate))).
					with("cache", map().
							with("paths", "/root/.m2/**/*"))
		        // @formatter:on
				)).environment(BuildEnvironment.builder().buildImage(STANDARD_3_0).build()).build();
		return buildProject;
	}

	private String name(String name, String envType) {
		return appName + "-" + name + "-" + envType;
	}

	private String id(String id, String envType) {
		return name(id, envType);
	}

	private IRole fromRoleName(String id, String roleName, String account) {
		return fromRoleArn(this, id, "arn:aws:iam::" + account + ":role/" + roleName,
		        FromRoleArnOptions.builder().mutable(false).build());
	}

	private IKey fromKeyName(String id, String key) {
		return Key.fromKeyArn(this, id, "arn:aws:kms:" + getRegion() + ":" + getAccount() + ":key/" + key);
	}

}
