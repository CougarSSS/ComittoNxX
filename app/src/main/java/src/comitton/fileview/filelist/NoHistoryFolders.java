package src.comitton.fileview.filelist;

import java.util.ArrayList;

import android.content.SharedPreferences;

import src.comitton.fileview.data.RecordItem;

/**
 * 閲覧履歴を残さないフォルダの登録と判定。
 * 登録したフォルダ(サブフォルダ含む)の本は、端末の履歴にも同期サーバーの履歴にも載せない。
 * 既読位置と栞は対象外(今までどおり記録・同期する)。
 */
public class NoHistoryFolders {
	private static final String KEY = "NoHistoryFolders";
	private static final String ENTRY_SEP = "\n";
	private static final String HOST_SEP = "\t";

	// 大文字小文字と / \ の違いを無視し、末尾の区切りを外す
	private static String normalize(String path) {
		if (path == null) {
			return "";
		}
		String s = path.replace('\\', '/').toLowerCase();
		while (s.length() > 0 && s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	private static String entry(String host, String path) {
		return (host == null ? "" : host.trim().toLowerCase()) + HOST_SEP + normalize(path);
	}

	public static ArrayList<String> load(SharedPreferences sp) {
		ArrayList<String> list = new ArrayList<String>();
		String saved = sp.getString(KEY, "");
		if (saved != null && !saved.isEmpty()) {
			for (String e : saved.split(ENTRY_SEP)) {
				if (!e.isEmpty()) {
					list.add(e);
				}
			}
		}
		return list;
	}

	private static void save(SharedPreferences sp, ArrayList<String> list) {
		StringBuilder sb = new StringBuilder();
		for (String e : list) {
			if (sb.length() > 0) {
				sb.append(ENTRY_SEP);
			}
			sb.append(e);
		}
		sp.edit().putString(KEY, sb.toString()).apply();
	}

	public static boolean isRegistered(SharedPreferences sp, String host, String folder) {
		return load(sp).contains(entry(host, folder));
	}

	public static void add(SharedPreferences sp, String host, String folder) {
		ArrayList<String> list = load(sp);
		String e = entry(host, folder);
		if (!list.contains(e)) {
			list.add(e);
			save(sp, list);
		}
	}

	public static void remove(SharedPreferences sp, String host, String folder) {
		ArrayList<String> list = load(sp);
		if (list.remove(entry(host, folder))) {
			save(sp, list);
		}
	}

	/** path(本のあるフォルダ)とfile(本の名前)から、本が登録フォルダの中(または登録フォルダそのもの)か判定する */
	public static boolean isExcluded(SharedPreferences sp, String host, String path, String file) {
		ArrayList<String> list = load(sp);
		if (list.isEmpty()) {
			return false;
		}
		String h = host == null ? "" : host.trim().toLowerCase();
		String book = normalize(path);
		String f = normalize(file);
		if (!f.isEmpty()) {
			book = book + "/" + f;
		}
		for (String e : list) {
			int p = e.indexOf(HOST_SEP);
			if (p < 0 || !e.substring(0, p).equals(h)) {
				continue;
			}
			String folder = e.substring(p + 1);
			// フォルダの境目で比べる(「/漫画」は「/漫画2」を含まない)
			if (book.equals(folder) || book.startsWith(folder + "/")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isExcluded(SharedPreferences sp, String host, RecordItem item) {
		return isExcluded(sp, host, item.getPath(), item.getFile());
	}
}
