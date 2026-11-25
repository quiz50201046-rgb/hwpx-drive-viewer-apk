package kr.hwpx.driveviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {

    private WebView webView;
    private boolean pageLoaded = false;
    private ValueCallback<Uri[]> fileUploadCallback;
    private static final int FILE_PICKER_CODE = 2001;

    private volatile byte[] pendingBytes = null;
    private volatile String pendingFileName = "구글드라이브_문서.hwpx";
    private volatile String pendingError = null;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/pending/", new WebViewAssetLoader.PathHandler() {
                @Override
                public WebResourceResponse handle(String path) {
                    byte[] data = pendingBytes;
                    if (data == null) return null;
                    return new WebResourceResponse(
                        "application/octet-stream",
                        null,
                        new ByteArrayInputStream(data)
                    );
                }
            })
            .build();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidHwpxBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageLoaded = true;
                notifyWebViewIfPending();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams
            ) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;
                Intent pickIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
                pickIntent.setType("*/*");
                startActivityForResult(pickIntent, FILE_PICKER_CODE);
                return true;
            }
        });

        handleIncomingIntent(getIntent());
        webView.loadUrl("https://appassets.androidplatform.net/assets/viewer.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            handleIncomingIntent(intent);
            if (pageLoaded) {
                notifyWebViewIfPending();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER_CODE && fileUploadCallback != null) {
            Uri uri = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
            fileUploadCallback.onReceiveValue(uri != null ? new Uri[]{uri} : null);
            fileUploadCallback = null;
        }
    }

    private void notifyWebViewIfPending() {
        if (pendingBytes != null || pendingError != null) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("window.loadFromAndroidBridge && window.loadFromAndroidBridge();", null);
                }
            });
        }
    }

    @SuppressWarnings("deprecation")
    private void handleIncomingIntent(Intent incoming) {
        if (incoming == null) return;
        String action = incoming.getAction();
        if (action == null) return;

        Uri targetUri = null;
        if (Intent.ACTION_VIEW.equals(action)) {
            targetUri = incoming.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            if (Build.VERSION.SDK_INT >= 33) {
                targetUri = incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                targetUri = incoming.getParcelableExtra(Intent.EXTRA_STREAM);
            }
        }

        if (targetUri == null) return;

        try {
            String name = queryDisplayName(targetUri);
            if (name == null || name.trim().isEmpty()) {
                name = "구글드라이브_문서.hwpx";
            }
            try (InputStream is = getContentResolver().openInputStream(targetUri)) {
                if (is == null) {
                    pendingError = "구글 드라이브 파일 스트림을 열 수 없습니다.";
                    return;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[16384];
                int nRead;
                while ((nRead = is.read(chunk, 0, chunk.length)) != -1) {
                    buffer.write(chunk, 0, nRead);
                }
                byte[] bytes = buffer.toByteArray();
                if (bytes.length > 0) {
                    pendingFileName = name;
                    pendingBytes = bytes;
                    pendingError = null;
                } else {
                    pendingError = "구글 드라이브 파일 데이터가 비어 있습니다.";
                }
            }
        } catch (Exception e) {
            String msg = e.getLocalizedMessage();
            pendingError = "구글 드라이브 파일 읽기 실패: " + (msg != null ? msg : "권한 오류");
        }
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null && !name.trim().isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        if (last != null && last.contains("/")) {
            return last.substring(last.lastIndexOf('/') + 1);
        }
        return last;
    }

    public class AndroidBridge {
        @JavascriptInterface
        public boolean isNativeApp() {
            return true;
        }

        @JavascriptInterface
        public boolean hasPendingFile() {
            return pendingBytes != null || pendingError != null;
        }

        @JavascriptInterface
        public String getPendingFileName() {
            return pendingFileName != null ? pendingFileName : "구글드라이브_문서.hwpx";
        }

        @JavascriptInterface
        public String getPendingError() {
            return pendingError != null ? pendingError : "";
        }

        @JavascriptInterface
        public void clearPendingFile() {
            pendingBytes = null;
            pendingError = null;
        }
    }
}