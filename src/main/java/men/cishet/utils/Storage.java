package men.cishet.utils;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Storage {
	public static FlatObjectContainer getOrCreateObjectContainer(String fileName) {
		return getOrCreateObjectContainer(new File(fileName));
	}

	private static FlatObjectContainer getOrCreateObjectContainer(File file) {
		tryMKFile(file);
		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
			return new FlatObjectContainer(file, (Serializable) ois.readObject());
		} catch (IOException | ClassNotFoundException | ClassCastException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static FlatRowContainer getOrCreateRowContainer(String fileName) {
		return getOrCreateRowContainer(new File("storage/" + fileName));
	}

	public static FlatRowContainer getOrCreateRowContainer(File file) {
		tryMKFile(file);
		return new FlatRowContainer(file, Arrays.stream(Utils.readFile(file).split("[\r\n]+"))
				.filter(l -> !l.isEmpty())
				.collect(Collectors.toList()));
	}

	private static void tryMKFile(File file) {
		File storageDir = new File("storage");
		if (!storageDir.exists()) {
			storageDir.mkdir();
		}
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public interface Container {
		File getFile();

		void save();
	}

	public static class FlatRowContainer implements Container {
		private final File file;
		private final List<String> rows;

		public FlatRowContainer(File file, List<String> rows) {
			this.file = file;
			this.rows = rows;
		}

		@Override
		public File getFile() {
			return file;
		}

		@Override
		public void save() {
			if (!file.exists()) {
				try {
					file.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			try (PrintStream writer = new PrintStream(file)) {
				rows.forEach(writer::println);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public List<String> getRows() {
			return rows;
		}

		public void addIfAbsent(String row) {
			if (!rows.contains(row)) {
				rows.add(row);
			}
		}

		public void removeIfPresent(String row) {
			while (rows.contains(row)) {
				rows.remove(row);
			}
		}
	}

	public static class FlatObjectContainer implements Container {
		private final File file;
		private final Serializable serial;

		public FlatObjectContainer(File file, Serializable serial) {
			this.file = file;
			this.serial = serial;
		}

		public Serializable getObject() {
			return serial;
		}

		@Override
		public File getFile() {
			return file;
		}

		@Override
		public void save() {
			if (!file.exists()) {
				try {
					file.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
				oos.writeObject(serial);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
