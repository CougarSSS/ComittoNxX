package src.comitton.fileview.filelist;

import java.util.ArrayList;

import android.content.SharedPreferences;

import src.comitton.fileview.data.RecordItem;

/**
 * 閲覧履歴を残さないフォルダの登録と判定。
 * 登録したフォルダ(サブフォルダ含む)の本は、端末の履歴にも同期サーバーの履歴にも載せない。
 * 既読位置と栞は対象外(今までどおり記録・同期する)。
 * 登録はパスだけで行い、接続先サーバーは区別しない(host引数は互換のために残してあり、使わない)。
 */
public class NoHistoryFolders {
	private static final String KEY = "NoHistoryFolders";
	private static final String ENTRY_SEP = "\n";
	private static final String HOST_SEP = "\t";	// 旧形式(host + TAB + パス)の読み込み用

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

	private static String entry(String path) {
		return normalize(path);
	}

	public static ArrayList<String> load(SharedPreferences sp) {
		ArrayList<String> list = new ArrayList<String>();
		String saved = sp.getString(KEY, "");
		if (saved != null && !saved.isEmpty()) {
			for (String e : saved.split(ENTRY_SEP)) {
				// 旧形式はサーバー名の部分を捨てる
				int p = e.indexOf(HOST_SEP);
				if (p >= 0) {
					e = e.substring(p + 1);
				}
				if (!e.isEmpty() && !list.contains(e)) {
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
		return load(sp).contains(entry(folder));
	}

	public static void add(SharedPreferences sp, String host, String folder) {
		ArrayList<String> list = load(sp);
		String e = entry(folder);
		if (!list.contains(e)) {
			list.add(e);
			save(sp, list);
		}
	}

	/** 設定画面用: 登録済みのパスそのもので外す */
	public static void removeEntry(SharedPreferences sp, String entry) {
		ArrayList<String> list = load(sp);
		if (list.remove(entry)) {
			save(sp, list);
		}
	}

	public static void remove(SharedPreferences sp, String host, String folder) {
		ArrayList<String> list = load(sp);
		if (list.remove(entry(folder))) {
			save(sp, list);
		}
	}

	/** path(本のあるフォルダ)とfile(本の名前)から、本が登録フォルダの中(または登録フォルダそのもの)か判定する */
	public static boolean isExcluded(SharedPreferences sp, String host, String path, String file) {
		ArrayList<String> list = load(sp);
		if (list.isEmpty()) {
			return false;
		}
		String book = normalize(path);
		String f = normalize(file);
		if (!f.isEmpty()) {
			book = book + "/" + f;
		}
		for (String folder : list) {
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
