package cloud.developing.iac.cicd;

import static java.util.regex.Pattern.quote;
import static java.util.stream.Collectors.toSet;

import java.io.FileInputStream;
import java.util.Properties;
import java.util.Set;

import org.javatuples.Pair;

public class Util {

	private static final String DEFAULT_FILE_MARKER = "";

	public static final Properties props(String appName) {
		String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
		Properties props = new Properties();
		try {
			props.load(
			        new FileInputStream(rootPath + "tools" + (appName.isBlank() ? "" : "-" + appName) + ".properties"));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		if (!DEFAULT_FILE_MARKER.equals(appName)) {
			var commonProps = props();
			commonProps.stringPropertyNames().forEach(key -> {
				if (props.getProperty(key) == null) {
					props.setProperty(key, commonProps.getProperty(key));
				}
			});
		}
		return props;
	}

	public static final Properties props() {
		return props(DEFAULT_FILE_MARKER);
	}

	public static final Set<String> envTypes(String appName) {
		return props(appName).stringPropertyNames().stream().map(key -> {
			var kk = key.split(quote("."));
			return new Pair<String, Integer>(kk[0], kk.length);
		}).filter(p -> p.getValue1() > 1).map(p -> p.getValue0()).collect(toSet());
	}

	public static void main(String[] args) {
		System.out.println(envTypes("ocr"));
	}

}
