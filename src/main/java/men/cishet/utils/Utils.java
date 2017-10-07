package men.cishet.utils;

import men.cishet.botcoins.Botcoins;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Utils {
	public static String readFile(File file) {
		if (file.exists()) {
			try (FileInputStream fis = new FileInputStream(file)) {
				return readStream(fis);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return "";
	}

	public static String readFile(String file) {
		return readFile(new File(file));
	}

	public static String readStream(InputStream s) {
		Scanner sc = new Scanner(s).useDelimiter("\\A");
		return sc.hasNext() ? sc.next() : "";
	}

	public static List<String> joinLists(List<String> list1, List<String> list2, String format) {
		if (list1.size() != list2.size()) {
			throw new IllegalArgumentException("Must be of the same length");
		}
		List<String> buffer = new ArrayList<>();
		for (int i = 0; i < list1.size(); i++) {
			buffer.add(String.format(format, list1.get(i), list2.get(i)));
		}
		return buffer;
	}


	public static String readResource(String resourcePath) {
		try (InputStream resourceStream = Botcoins.class.getResourceAsStream(resourcePath)) {
			return readStream(resourceStream);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}

	public static String round(double number, int precision) {
		return String.format("%." + precision + "f", number);
	}

	public static String formatAndRound(double number, int precision) {
		return String.format("%,." + precision + "f", number);
	}

	public static List<String> frontPadToLongest(List<String> list, final String format, final char whitespace) {
		AtomicInteger longest = new AtomicInteger(0);
		list.stream()
				.map(String::length)
				.sorted(Comparator.comparingInt(s -> -s))
				.findFirst()
				.ifPresent(longest::set);
		return list.stream()
				.map(str -> {
					int pads = longest.get() - str.length();
					char[] chars = new char[pads];
					Arrays.fill(chars, whitespace);
					return String.format(format == null || format.isEmpty() ? "%s" : format, new String(chars) + str);
				})
				.collect(Collectors.toList());
	}
}
