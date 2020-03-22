package cloud.developing.iac.common;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CdkMap implements Map<String, Object> {

	private Map<String, Object> map = new HashMap<>();

	public static final CdkMap map() {
		return new CdkMap();
	}

	public CdkMap with(String key, Object value) {
		map.put(key, value);
		return this;
	}

	public CdkMap with(boolean ignore, String key, Object value) {
		if (!ignore) {
			map.put(key, value);
		}
		return this;
	}

	@Override
	public Object put(String key, Object value) {
		return map.put(key, value);
	}

	public int size() {
		return map.size();
	}

	public boolean isEmpty() {
		return map.isEmpty();
	}

	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	public boolean containsValue(Object value) {
		return map.containsValue(value);
	}

	public Object get(Object key) {
		return map.get(key);
	}

	public Object remove(Object key) {
		return map.remove(key);
	}

	public void putAll(Map<? extends String, ? extends Object> m) {
		map.putAll(m);
	}

	public void clear() {
		map.clear();
	}

	public Set<String> keySet() {
		return map.keySet();
	}

	public Collection<Object> values() {
		return map.values();
	}

	public Set<Entry<String, Object>> entrySet() {
		return map.entrySet();
	}

}
