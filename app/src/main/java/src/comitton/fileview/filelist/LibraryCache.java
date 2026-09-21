package src.comitton.fileview.filelist;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import src.comitton.common.DEF;
import src.comitton.common.Logcat;
import src.comitton.fileaccess.EverythingLibraryClient;
import src.comitton.fileview.data.LibraryEntry;

/**
 * 書庫管理タブ(TYPE_LIBRARY)用のフラットなアーカイブ一覧のキャッシュ/永続化/グルーピングを担当する。
 *
 * RecordList汎用の.dat機構(FILENAME[]索引によるper-type単一ファイル)は使わず、
 * ここで独自に <conf>/library.dat (TSV: path\tname\tsize\tdateModified) に永続化する。
 * 個々のアーカイブエントリをフラットなまま保存し、作品単位のグルーピングは表示のたびに
 * buildWorkList()で計算する(検証済みPythonスクリプトのロジックをそのまま移植)。
 */
public class LibraryCache {
    private static final String TAG = "LibraryCache";
    private static final String FILENAME = "library.dat";
    private static final String SEPARATOR = "\t";

    // 漫画フォルダ: 相対フォルダパス(サブフォルダ込み)の先頭についた[XX]配布者タグを除去して作品名にする
    private static final Pattern MANGA_PREFIX_PATTERN = Pattern.compile("^\\[[A-Za-z]{2}\\]");
    // Aria2c_DL: ファイル名(拡張子除去後)から巻数表記を除去して作品名を抽出する
    private static final Pattern ARIA_EXT_PATTERN = Pattern.compile("\\.(zip|rar|cbz)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARIA_VOL_PATTERN = Pattern.compile(
            "^(?<title>.+?)[\\s_]*(?:(?:@COMIC|THE\\s+COMIC)\\s*)?(?:[Vv]\\d{1,3}|第\\d{1,3}巻)s?(?:\\s+DL)?$");

    private static ArrayList<LibraryEntry> sCache;
    // buildWorkList()の結果キャッシュ。作品数×全体正規表現グルーピングは全1万件超だと
    // メインスレッドで数秒かかりうる重い処理のため、タブ再表示/巻オープンのたびに
    // 再計算しない(merge()でデータが変わった時だけ無効化する)。
    private static ArrayList<LibraryWork> sWorkListCache;
    private static boolean sWorkListDirty = true;

    private LibraryCache() {
    }

    private static String getFilePath() {
        return DEF.getConfigDirectory() + FILENAME;
    }

    /**
     * メモリキャッシュ(初回呼び出し時にディスクから遅延ロード)を返す。
     * 呼び出し元はこのリストを直接書き換えないこと(merge()経由で更新する)。
     */
    public static synchronized ArrayList<LibraryEntry> getEntries() {
        if (sCache == null) {
            sCache = loadFromDisk();
        }
        return sCache;
    }

    /**
     * 指定エントリ((path, name)一致)をキャッシュから削除して保存する。
     * @return 実際に削除した件数
     */
    public static synchronized int removeEntries(List<LibraryEntry> targets) {
        if (targets == null || targets.isEmpty()) {
            return 0;
        }
        java.util.HashSet<String> keys = new java.util.HashSet<>();
        for (LibraryEntry t : targets) {
            keys.add(t.key());
        }
        ArrayList<LibraryEntry> current = getEntries();
        int before = current.size();
        for (int i = current.size() - 1; i >= 0; i--) {
            if (keys.contains(current.get(i).key())) {
                current.remove(i);
            }
        }
        int removed = before - current.size();
        if (removed > 0) {
            saveToDisk(current);
            sWorkListDirty = true;
        }
        Logcat.w(Logcat.LOG_LEVEL_WARN, "removeEntries: 対象" + targets.size() + "件中" + removed + "件を削除");
        return removed;
    }

    /**
     * キャッシュを空にする(メモリ・ディスク両方)。
     * サーバー側で大量のファイル名変更/整理が行われた場合、merge()の
     * 「追加・更新のみで削除しない」仕様では対応できない(旧ファイル名の
     * エントリが永久に残る)ため、この場合は一度空にしてから
     * importFromJsonFile()で全件を入れ直す。
     */
    public static synchronized void clearAll() {
        int logLevel = Logcat.LOG_LEVEL_WARN;
        sCache = new ArrayList<>();
        sWorkListCache = null;
        sWorkListDirty = true;
        File file = new File(getFilePath());
        if (file.exists() && !file.delete()) {
            Logcat.e(logLevel, "clearAll: " + file.getAbsolutePath() + " の削除に失敗しました");
        }
        Logcat.w(logLevel, "clearAll: 書庫管理キャッシュを空にしました");
    }

    private static ArrayList<LibraryEntry> loadFromDisk() {
        int logLevel = Logcat.LOG_LEVEL_WARN;
        ArrayList<LibraryEntry> list = new ArrayList<>();
        String filepath = getFilePath();
        File file = new File(filepath);
        if (!file.exists()) {
            return list;
        }
        int skipped = 0;
        try {
            FileInputStream is = new FileInputStream(filepath);
            InputStreamReader sr = new InputStreamReader(is, "UTF-8");
            BufferedReader br = new BufferedReader(sr, 8192);
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(SEPARATOR, -1);
                if (parts.length < 4) {
                    skipped++;
                    continue;
                }
                try {
                    String path = parts[0];
                    String name = parts[1];
                    long size = Long.parseLong(parts[2]);
                    long date = Long.parseLong(parts[3]);
                    list.add(new LibraryEntry(path, name, size, date));
                }
                catch (NumberFormatException ex) {
                    skipped++;
                    continue;
                }
            }
            br.close();
            sr.close();
            is.close();
            Logcat.w(logLevel, "loadFromDisk: " + filepath + " から" + list.size() + "件読み込み(不正行スキップ" + skipped + "件)");
        }
        catch (Exception ex) {
            Logcat.e(logLevel, "loadFromDisk失敗: " + filepath, ex);
        }
        return list;
    }

    private static void saveToDisk(ArrayList<LibraryEntry> list) {
        int logLevel = Logcat.LOG_LEVEL_WARN;
        String filepath = getFilePath();
        File dir = new File(DEF.getConfigDirectory());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            FileOutputStream os = new FileOutputStream(filepath, false);
            OutputStreamWriter sw = new OutputStreamWriter(os, "UTF-8");
            BufferedWriter bw = new BufferedWriter(sw, 8192);
            for (LibraryEntry e : list) {
                bw.write(sanitize(e.getPath()) + SEPARATOR + sanitize(e.getName()) + SEPARATOR + e.getSize() + SEPARATOR + e.getDateModified());
                bw.newLine();
            }
            bw.flush();
            bw.close();
            sw.close();
            os.close();
            Logcat.w(logLevel, "saveToDisk: " + filepath + " へ" + list.size() + "件保存");
        }
        catch (Exception ex) {
            Logcat.e(logLevel, "saveToDisk失敗: " + filepath, ex);
        }
    }

    // TSVを壊さないよう、ファイル名/パスに万一タブや改行が含まれていても除去しておく
    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\t", " ").replace("\n", " ").replace("\r", " ");
    }

    /**
     * 新規/更新エントリをキャッシュへマージする。(path, name)が一致する既存エントリは上書き、
     * 無ければ追加する。既存エントリを削除することは無い(リフレッシュのlimit=200が最新分のみ
     * 返してきても、それ以前に取り込んだ分は保持され続ける)。
     * @return 新規追加件数
     */
    public static synchronized int merge(List<LibraryEntry> newEntries) {
        int logLevel = Logcat.LOG_LEVEL_WARN;
        if (newEntries == null || newEntries.isEmpty()) {
            Logcat.w(logLevel, "merge: newEntriesが空のため何もしない");
            return 0;
        }
        ArrayList<LibraryEntry> current = getEntries();
        HashMap<String, Integer> indexByKey = new HashMap<>();
        for (int i = 0; i < current.size(); i++) {
            indexByKey.put(current.get(i).key(), i);
        }
        int added = 0;
        int updated = 0;
        for (LibraryEntry e : newEntries) {
            Integer idx = indexByKey.get(e.key());
            if (idx != null) {
                current.set(idx, e);
                updated++;
            }
            else {
                indexByKey.put(e.key(), current.size());
                current.add(e);
                added++;
            }
        }
        saveToDisk(current);
        sWorkListDirty = true;
        Logcat.w(logLevel, "merge: 受信" + newEntries.size() + "件 → 新規" + added + "件、更新" + updated + "件、マージ後合計" + current.size() + "件");
        return added;
    }

    /**
     * 初回一括インポート用。EverythingLibraryClient.Responseと同じJSON形状
     * ({"total_results":..., "results":[{"name","path","size","date_modified",...}, ...]})
     * のファイルを読み込み、merge()と同じロジックで取り込む。
     * 開発者専用の使い捨て機能のため、呼び出し口はUI未実装(現状は手動でこのメソッドを呼ぶ前提)。
     * @return 新規追加件数
     */
    public static int importFromJsonFile(String jsonFilePath) throws IOException {
        int logLevel = Logcat.LOG_LEVEL_WARN;
        Logcat.w(logLevel, "importFromJsonFile開始: " + jsonFilePath);
        String json;
        try {
            json = readWholeFile(jsonFilePath);
        }
        catch (IOException ex) {
            Logcat.e(logLevel, "importFromJsonFileファイル読み込み失敗: " + jsonFilePath, ex);
            throw ex;
        }
        EverythingLibraryClient.Response resp;
        try {
            Gson gson = new Gson();
            resp = gson.fromJson(json, EverythingLibraryClient.Response.class);
        }
        catch (Exception ex) {
            Logcat.e(logLevel, "importFromJsonFile JSONパース失敗: " + jsonFilePath, ex);
            return 0;
        }
        if (resp == null || resp.results == null) {
            Logcat.w(logLevel, "importFromJsonFile: results無し(0件)");
            return 0;
        }
        ArrayList<LibraryEntry> entries = EverythingLibraryClient.toEntries(resp.results);
        Logcat.w(logLevel, "importFromJsonFile: JSON内results=" + resp.results.size() + "件 → 変換後entries=" + entries.size() + "件");
        int added = merge(entries);
        Logcat.w(logLevel, "importFromJsonFile完了: 新規" + added + "件");
        return added;
    }

    private static String readWholeFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        FileInputStream is = new FileInputStream(path);
        InputStreamReader sr = new InputStreamReader(is, "UTF-8");
        BufferedReader br = new BufferedReader(sr, 65536);
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        sr.close();
        is.close();
        return sb.toString();
    }

    /**
     * 作品(タイトル)単位でグルーピングした結果。
     */
    public static class LibraryWork {
        public String title;
        public ArrayList<LibraryEntry> volumes;
        public long latestDate;
    }

    /**
     * フラットなエントリ一覧を作品単位でグルーピングし、最新巻の更新日時降順で返す。
     * グルーピングロジックは実データ(10,769件)で検証済みのPythonスクリプトをそのまま移植したもの。
     */
    public static synchronized ArrayList<LibraryWork> buildWorkList(ArrayList<LibraryEntry> flatList) {
        if (!sWorkListDirty && sWorkListCache != null) {
            return sWorkListCache;
        }
        int logLevel = Logcat.LOG_LEVEL_WARN;
        // [0]=Aria2c_DLの巻数表記フォールバック件数, [1]=Aria2c_DL総件数, [2]=想定外パス件数
        int[] fallbackStat = new int[3];
        LinkedHashMap<String, ArrayList<LibraryEntry>> groups = new LinkedHashMap<>();
        for (LibraryEntry e : flatList) {
            String title = resolveTitle(e, fallbackStat);
            ArrayList<LibraryEntry> g = groups.get(title);
            if (g == null) {
                g = new ArrayList<>();
                groups.put(title, g);
            }
            g.add(e);
        }

        ArrayList<LibraryWork> works = new ArrayList<>();
        for (Map.Entry<String, ArrayList<LibraryEntry>> ent : groups.entrySet()) {
            LibraryWork w = new LibraryWork();
            w.title = ent.getKey();
            w.volumes = ent.getValue();
            long latest = 0;
            for (LibraryEntry v : w.volumes) {
                if (v.getDateModified() > latest) {
                    latest = v.getDateModified();
                }
            }
            w.latestDate = latest;
            works.add(w);
        }

        Collections.sort(works, new Comparator<LibraryWork>() {
            @Override
            public int compare(LibraryWork a, LibraryWork b) {
                return Long.compare(b.latestDate, a.latestDate);
            }
        });
        Logcat.w(logLevel, "buildWorkList: " + flatList.size() + "件 → " + works.size() + "作品にグルーピング"
                + "(Aria2c_DL巻数パターン未マッチ(単体作品フォールバック)=" + fallbackStat[0] + "/" + fallbackStat[1]
                + ", 想定外パス=" + fallbackStat[2] + ")");
        sWorkListCache = works;
        sWorkListDirty = false;
        return works;
    }

    private static String resolveTitle(LibraryEntry e) {
        return resolveTitle(e, null);
    }

    // fallbackStatが非nullの場合、[0]=Aria2c_DLフォールバック件数, [1]=Aria2c_DL総件数,
    // [2]=想定外パス件数を加算する(buildWorkList()の集計ログ用)。
    private static String resolveTitle(LibraryEntry e, int[] fallbackStat) {
        String path = e.getPath() != null ? e.getPath() : "";
        String name = e.getName() != null ? e.getName() : "";

        if (path.startsWith(EverythingLibraryClient.LIBRARY_PATH_MANGA)) {
            String folder = path.length() > EverythingLibraryClient.LIBRARY_PATH_MANGA.length()
                    ? path.substring(EverythingLibraryClient.LIBRARY_PATH_MANGA.length() + 1)
                    : "";
            Matcher m = MANGA_PREFIX_PATTERN.matcher(folder);
            return normalizeWhitespace(m.replaceFirst(""));
        }
        else if (path.startsWith(EverythingLibraryClient.LIBRARY_PATH_ARIA2C)) {
            if (fallbackStat != null) {
                fallbackStat[1]++;
            }
            String base = ARIA_EXT_PATTERN.matcher(name).replaceAll("");
            Matcher m = ARIA_VOL_PATTERN.matcher(base);
            if (m.matches()) {
                String title = m.group("title");
                // Python版の .rstrip('_').rstrip() と同じ順序で除去する(意図的にこの順)
                title = rstripChar(title, '_');
                title = rstripWhitespace(title);
                return normalizeWhitespace(title);
            }
            // 巻数表記にマッチしなかった場合は単体作品としてファイル名そのものを作品名にする
            if (fallbackStat != null) {
                fallbackStat[0]++;
            }
            return normalizeWhitespace(base);
        }
        else {
            // 想定外のパス(通常は発生しない: /list はmanga/aria2cの2パスのみ問い合わせている)
            if (fallbackStat != null) {
                fallbackStat[2]++;
            }
            Logcat.w(Logcat.LOG_LEVEL_WARN, "resolveTitle: 想定外のパスです(manga/Aria2c_DL以外): " + path);
            return normalizeWhitespace(name.isEmpty() ? path : name);
        }
    }

    // ファイル名の表記ゆれ(連続する半角/全角スペースの個数違いなど)を軽く吸収するための正規化。
    // タイトルの意味自体が異なるもの(表記の大幅な違い)までは吸収しない ―― あくまで
    // 前後の空白除去+連続する空白を半角スペース1個にまとめる程度に留める。
    private static String normalizeWhitespace(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().replaceAll("[\\s　]+", " ");
    }

    // 書庫管理タブの巻一覧の並び替え専用。DEF.compareFileName()自体はアプリ全体で
    // 共用されているため変更せず、ここで比較対象の文字列だけ空白正規化してから渡す
    // (前後の空白除去+連続空白を1個化。ファイル名に含まれる余分なスペースの個数違いで
    // 巻の並びが崩れるのを防ぐ)。
    public static int compareVolumeName(String name1, String name2) {
        return DEF.compareFileName(normalizeWhitespace(name1), normalizeWhitespace(name2));
    }

    private static String rstripChar(String s, char c) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == c) {
            end--;
        }
        return s.substring(0, end);
    }

    private static String rstripWhitespace(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }
}
