package src.comitton.fileaccess;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import src.comitton.common.DEF;
import src.comitton.common.Logcat;
import src.comitton.fileview.data.LibraryEntry;

/**
 * 書庫管理タブ用、EverythingX拡張APIの /list エンドポイントに対するクライアント。
 * EverythingClient(/?search=)と同じホスト/認証設定を再利用しつつ、対象を
 * 漫画フォルダとAria2c_DLフォルダのzip/rar/cbzに固定して問い合わせる。
 * /list はページング非対応・常にmtime降順ソート・limitは既定200(最大2000)というAPI仕様のため、
 * それ以上の絞り込みは行わない。
 */
public class EverythingLibraryClient {
    private static final String TAG = "EverythingLibraryClient";

    public static final String LIBRARY_PATH_MANGA = "/srv/samba/漫画";
    public static final String LIBRARY_PATH_ARIA2C = "/srv/samba/Aria2c_DL";
    private static final String[] LIBRARY_EXTS = {"zip", "rar", "cbz"};
    public static final int DEFAULT_LIMIT = 200;

    public static class Response {
        @SerializedName("total_results")
        public String totalResults;
        public List<Result> results;
    }

    public static class Result {
        public String type;
        public String name;
        public String path;
        // EverythingClientと同じ理由(空文字列でGsonがNumberFormatExceptionを投げるのを防ぐ)でString型
        public String size;
        @SerializedName("date_modified")
        public String dateModified;
    }

    private static long parseLongSafe(String s) {
        if (s == null || s.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(s);
        }
        catch (NumberFormatException e) {
            return 0L;
        }
    }

    // /listレスポンスのResult一覧をLibraryEntry一覧に変換する。
    // 初回一括インポート(同形式のJSONファイル)からも共通で呼べるようpublic staticにしている。
    public static ArrayList<LibraryEntry> toEntries(List<Result> results) {
        ArrayList<LibraryEntry> entries = new ArrayList<>();
        if (results == null) {
            return entries;
        }
        for (Result result : results) {
            String name = result.name != null ? result.name : "";
            if (name.isEmpty()) {
                // フォルダ等、名前が取れない結果は対象外(zip/rar/cbzのみ扱うため通常発生しない想定)
                continue;
            }
            String path = result.path != null ? result.path : "";
            long size = parseLongSafe(result.size);
            long dateModified = parseLongSafe(result.dateModified);
            entries.add(new LibraryEntry(path, name, size, dateModified));
        }
        return entries;
    }

    public static void list(final Context context, final Handler handler, final SharedPreferences sharedPreferences) {
        list(context, DEFAULT_LIMIT, handler, sharedPreferences);
    }

    public static void list(final Context context, final int limit, final Handler handler, final SharedPreferences sharedPreferences) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int logLevel = Logcat.LOG_LEVEL_WARN;
                String host = sharedPreferences.getString(DEF.KEY_EVERYTHING_HOST, "");
                String port = sharedPreferences.getString(DEF.KEY_EVERYTHING_PORT, "8080");
                String user = sharedPreferences.getString(DEF.KEY_EVERYTHING_USER, "");
                String pass = sharedPreferences.getString(DEF.KEY_EVERYTHING_PASS, "");

                if (host.isEmpty()) {
                    sendError(handler, "Everything host is not configured.");
                    return;
                }

                try {
                    StringBuilder urlBuilder = new StringBuilder();
                    urlBuilder.append("http://").append(host).append(":").append(port).append("/list?");
                    urlBuilder.append("path=").append(URLEncoder.encode(LIBRARY_PATH_MANGA, "UTF-8")).append("&");
                    urlBuilder.append("path=").append(URLEncoder.encode(LIBRARY_PATH_ARIA2C, "UTF-8")).append("&");
                    for (String ext : LIBRARY_EXTS) {
                        urlBuilder.append("ext=").append(ext).append("&");
                    }
                    urlBuilder.append("limit=").append(limit);
                    urlBuilder.append("&json=1&path_column=1&size_column=1&date_modified_column=1");
                    String urlStr = urlBuilder.toString();
                    Logcat.w(logLevel, "Everything Library API URL: " + urlStr);

                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(15000);

                    if (!user.isEmpty() && !pass.isEmpty()) {
                        String auth = user + ":" + pass;
                        String basicAuth = "Basic " + android.util.Base64.encodeToString(auth.getBytes(), android.util.Base64.NO_WRAP);
                        conn.setRequestProperty("Authorization", basicAuth);
                    }

                    int responseCode = conn.getResponseCode();
                    Logcat.w(logLevel, "Everything Library API response code: " + responseCode);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder response = new StringBuilder();
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();

                        Response apiResponse;
                        try {
                            Gson gson = new Gson();
                            apiResponse = gson.fromJson(response.toString(), Response.class);
                        }
                        catch (Exception parseException) {
                            // JSONパース失敗(レスポンス形式の想定外変化など)。レスポンス本文の先頭だけ
                            // ログに残す(全文は大きい場合があるため)
                            int previewLen = Math.min(response.length(), 500);
                            Logcat.e(logLevel, "Everything Library list JSON parse error. response(先頭" + previewLen + "文字)=" + response.substring(0, previewLen), parseException);
                            sendError(handler, "Parse error: " + parseException.getMessage());
                            return;
                        }

                        ArrayList<LibraryEntry> entries = toEntries(apiResponse != null ? apiResponse.results : null);
                        Logcat.w(logLevel, "Everything Library list: results=" + (apiResponse != null && apiResponse.results != null ? apiResponse.results.size() : 0) + ", entries(name有効)=" + entries.size());

                        Message msg = handler.obtainMessage(DEF.HMSG_LIBRARY_RESULT, entries);
                        handler.sendMessage(msg);
                    }
                    else {
                        Logcat.e(logLevel, "Everything Library list HTTP error: " + responseCode);
                        sendError(handler, "HTTP Error: " + responseCode);
                    }
                }
                catch (Exception e) {
                    Logcat.e(logLevel, "Everything Library list error", e);
                    sendError(handler, "Error: " + e.getMessage());
                }
            }
        }).start();
    }

    private static void sendError(Handler handler, String error) {
        Message msg = handler.obtainMessage(DEF.HMSG_TOAST, error);
        handler.sendMessage(msg);
    }
}
