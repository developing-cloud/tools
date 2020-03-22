package cloud.developing.iac.cicd;

import static cloud.developing.iac.cicd.Util.*;

import java.util.Properties;

import software.amazon.awscdk.core.App;

public class CicdApp {

	public static void main(String[] args) {
		var app = new App();
		var props = props("ocr");
		var appName = getAppName(props);
		new CicdStack(app, appName, props);

		executeStack(app, props("metadata-extraction"));
		executeStack(app, props("db"));
		executeStack(app, props("vehicle-finder"));
		executeStack(app, props("notifier"));
		executeStack(app, props("data-collection"));
		executeStack(app, props("merge-adapter"));
		executeStack(app, props("toll-registry"));

		app.synth();
	}

	private static void executeStack(App app, Properties props) {
		new CicdStack(app, getAppName(props), props);
	}

	private static String getAppName(Properties props) {
		return props.getProperty("app-name");
	}

}
