package cloud.developing.iac.iam;

import software.amazon.awscdk.core.App;

public class IamApp {

	public static void main(String[] args) {
		var app = new App();
		var envType = args[0];
		new CommonRolesStack(app, "common-roles-" + envType);
		app.synth();
	}

}
