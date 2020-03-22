package cloud.developing.iac.iam;

import static cloud.developing.iac.cicd.Util.props;
import static software.amazon.awscdk.services.iam.ManagedPolicy.fromAwsManagedPolicyName;
import static software.amazon.awscdk.services.iam.PolicyStatement.Builder.create;
import static software.amazon.awscdk.services.iam.RoleProps.builder;

import java.util.List;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.services.iam.AccountPrincipal;
import software.amazon.awscdk.services.iam.CompositePrincipal;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;

public class CommonRolesStack extends Stack {

	CommonRolesStack(Construct scope, String id) {
		super(scope, id);
		var cicdAccount = props().getProperty("cicd-account");
		createRoles(cicdAccount);

	}

	private void createRoles(String cicdAccount) {
		var deploymentRoleName = "deployment-role";
		var deploymentRoleProps = builder().roleName(deploymentRoleName)
		        .assumedBy(new CompositePrincipal(new AccountPrincipal(cicdAccount),
		                new ServicePrincipal("cloudformation.amazonaws.com")))
		        .build();

		// TODO Overly permissive
		var deploymentRole = new Role(this, deploymentRoleName, deploymentRoleProps);
		deploymentRole.addManagedPolicy(fromAwsManagedPolicyName("AWSLambdaFullAccess"));
		deploymentRole.addManagedPolicy(fromAwsManagedPolicyName("AWSCloudFormationFullAccess"));
		deploymentRole.addManagedPolicy(fromAwsManagedPolicyName("AmazonDynamoDBFullAccess"));
		deploymentRole.addToPolicy(create()
		        .actions(List.of("kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*", "kms:GenerateDataKey*",
		                "kms:DescribeKey", "ssm:Put*", "ssm:Delete*", "ssm:Describe*", "ssm:AddTagsToResource",
		                "sns:GetTopicAttributes", "secretsmanager:CreateSecret", "secretsmanager:TagResource",
		                "secretsmanager:DeleteSecret", "iam:CreateRole", "iam:DeleteRole", "iam:PutRolePolicy",
		                "iam:DeleteRolePolicy", "states:CreateStateMachine", "states:TagResource"))
		        .resources(List.of("*")).build());

		var assumedByLambda = new ServicePrincipal("lambda.amazonaws.com");
		var lambdaExecutePolicy = fromAwsManagedPolicyName("AWSLambdaExecute");

		var ocrLambdaExecutionRoleName = "ocr-lambda-execution-role";
		var ocrLambdaExecutionRoleProps = builder().roleName(ocrLambdaExecutionRoleName).assumedBy(assumedByLambda)
		        .build();
		var ocrLambdaExecutionRole = new Role(this, ocrLambdaExecutionRoleName, ocrLambdaExecutionRoleProps);

		ocrLambdaExecutionRole.addManagedPolicy(lambdaExecutePolicy);
		ocrLambdaExecutionRole
		        .addToPolicy(create().actions(List.of("rekognition:DetectText")).resources(List.of("*")).build());

		var mdLambdaExecutionRoleName = "metadata-extraction-lambda-execution-role";
		var mdLambdaExecutionRoleProps = builder().roleName(mdLambdaExecutionRoleName).assumedBy(assumedByLambda)
		        .build();
		new Role(this, mdLambdaExecutionRoleName, mdLambdaExecutionRoleProps).addManagedPolicy(lambdaExecutePolicy);

		var dbLambdaExecutionRoleName = "db-lambda-execution-role";
		var dbLambdaExecutionRoleProps = builder().roleName(dbLambdaExecutionRoleName).assumedBy(assumedByLambda)
		        .build();
		var dbLambdaExecutionRole = new Role(this, dbLambdaExecutionRoleName, dbLambdaExecutionRoleProps);
		dbLambdaExecutionRole.addManagedPolicy(lambdaExecutePolicy);
		dbLambdaExecutionRole.addToPolicy(create().actions(List.of("dynamodb:Put*"))
		        .resources(List.of("arn:aws:dynamodb:*:*:table/*vehicle*")).build());

		var vfLambdaExecutionRoleName = "vehicle-finder-lambda-execution-role";
		var vfLambdaExecutionRoleProps = builder().roleName(vfLambdaExecutionRoleName).assumedBy(assumedByLambda)
		        .build();
		var vfLambdaExecutionRole = new Role(this, vfLambdaExecutionRoleName, vfLambdaExecutionRoleProps);
		vfLambdaExecutionRole.addManagedPolicy(lambdaExecutePolicy);
		vfLambdaExecutionRole.addToPolicy(create().actions(List.of("dynamodb:Query"))
		        .resources(List.of("arn:aws:dynamodb:*:*:table/*vehicle*")).build());
		vfLambdaExecutionRole
		        .addToPolicy(create().actions(List.of("secretsmanager:Get*")).resources(List.of("*")).build());

		var notifierLambdaExecutionRoleName = "notifier-lambda-execution-role";
		var notifierLambdaExecutionRoleProps = builder().roleName(notifierLambdaExecutionRoleName)
		        .assumedBy(assumedByLambda).build();
		var notifierLambdaExecutionRole = new Role(this, notifierLambdaExecutionRoleName,
		        notifierLambdaExecutionRoleProps);
		notifierLambdaExecutionRole.addManagedPolicy(lambdaExecutePolicy);
		notifierLambdaExecutionRole.addToPolicy(
		        create().actions(List.of("SNS:Publish")).resources(List.of("arn:aws:sns:*:*:notifier-*")).build());

		var dcLambdaExecutionRoleName = "dc-lambda-execution-role";
		var dcLambdaExecutionRoleProps = builder().roleName(dcLambdaExecutionRoleName).assumedBy(assumedByLambda)
		        .build();
		var dcLambdaExecutionRole = new Role(this, dcLambdaExecutionRoleName, dcLambdaExecutionRoleProps);
		dcLambdaExecutionRole.addManagedPolicy(lambdaExecutePolicy);
		dcLambdaExecutionRole.addToPolicy(create().actions(List.of("states:StartExecution"))
		        .resources(List.of("arn:aws:states:*:*:stateMachine:toll-registry*")).build());
		var basicLambdaExecutionRoleName = "basic-lambda-execution-role";
		var basicLambdaExecutionRoleProps = builder().roleName(basicLambdaExecutionRoleName).assumedBy(assumedByLambda)
		        .build();
		new Role(this, basicLambdaExecutionRoleName, basicLambdaExecutionRoleProps)
		        .addManagedPolicy(fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));

	}

}
