package com.doubletapcopy;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashMap;
import java.util.Map;

public class WeChatCopyService extends AccessibilityService {

    private static final String TAG = "DoubleTapCopy";
    private static final long DOUBLE_TAP_TIMEOUT = 500; // ms
    private static final String[] WECHAT_PACKAGES = {
            "com.tencent.mm",
    };

    private final Map<String, Long> lastClickMap = new HashMap<>();
    private Vibrator vibrator;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vm != null) {
                vibrator = vm.getDefaultVibrator();
            }
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        Log.d(TAG, "服务已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 只处理微信的点击事件
        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (!isWeChat(pkg)) return;

        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return;

        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return;

        // 找到这条消息的容器节点
        AccessibilityNodeInfo messageNode = findMessageContainer(source);
        if (messageNode == null) return;

        String signature = makeNodeSignature(messageNode);
        long now = System.currentTimeMillis();
        Long lastTime = lastClickMap.get(signature);

        if (lastTime != null && (now - lastTime) < DOUBLE_TAP_TIMEOUT) {
            // 双击确认！提取文字并复制
            String text = extractAllText(messageNode);
            lastClickMap.remove(signature);

            if (!TextUtils.isEmpty(text)) {
                copyToClipboard(text);
                showToast("已复制");
                hapticFeedback();
            }
        } else {
            lastClickMap.put(signature, now);
        }

        cleanupMap(now);

        messageNode.recycle();
    }

    @Override
    public void onInterrupt() {
    }

    // === 微信包名判断 ===

    private boolean isWeChat(String pkg) {
        for (String wp : WECHAT_PACKAGES) {
            if (wp.equals(pkg)) return true;
        }
        return false;
    }

    // === 查找消息容器 ===

    private AccessibilityNodeInfo findMessageContainer(AccessibilityNodeInfo node) {
        // 向上查找，找到消息气泡的容器
        AccessibilityNodeInfo current = node;
        AccessibilityNodeInfo best = null;
        int maxLevels = 6;

        for (int i = 0; i < maxLevels && current != null; i++) {
            String className = current.getClassName() != null ? current.getClassName().toString() : "";

            // 有文字内容的节点，记下来
            if (current.getChildCount() > 0 && hasTextContent(current)) {
                if (best != null) best.recycle();
                best = current;
                current = current.getParent();
                // 不 recycle 因为 current 就是 best
            } else {
                // 跳过没有内容的中间层
                AccessibilityNodeInfo parent = current.getParent();
                if (current != best) {
                    current.recycle();
                }
                current = parent;
            }
        }

        // 如果找到了多层的，取最外层的那个
        return best;
    }

    private boolean hasTextContent(AccessibilityNodeInfo node) {
        if (node == null) return false;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) return true;
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.length() > 0) return true;

        // 检查子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean childHasText = hasTextContent(child);
                child.recycle();
                if (childHasText) return true;
            }
        }
        return false;
    }

    // === 节点签名（用于判断两次点击是否在同一个消息上）===

    private String makeNodeSignature(AccessibilityNodeInfo node) {
        String cls = node.getClassName() != null ? node.getClassName().toString() : "";
        android.graphics.Rect rect = new android.graphics.Rect();
        node.getBoundsInScreen(rect);
        String text = node.getText() != null ? node.getText().toString() : "";
        // class + 位置 + 文字前缀 = 唯一标识
        return cls + "|" + rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom + "|" + text.substring(0, Math.min(text.length(), 20));
    }

    // === 提取所有文字 ===

    private String extractAllText(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();
        collectText(node, sb);
        String result = sb.toString().trim();
        // 把多个换行压缩一下
        result = result.replaceAll("\\n{3,}", "\n\n");
        return result;
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;

        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(text);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectText(child, sb);
                child.recycle();
            }
        }
    }

    // === 复制到剪贴板 ===

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("wechat_msg", text);
            clipboard.setPrimaryClip(clip);
            Log.d(TAG, "已复制: " + text.substring(0, Math.min(text.length(), 50)));
        }
    }

    // === 振动反馈 ===

    private void hapticFeedback() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(50);
        }
    }

    // === Toast ===

    private void showToast(String msg) {
        android.os.Handler handler = new android.os.Handler(getMainLooper());
        handler.post(() -> android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show());
    }

    // === 清理过期记录 ===

    private void cleanupMap(long now) {
        lastClickMap.entrySet().removeIf(e -> (now - e.getValue()) > DOUBLE_TAP_TIMEOUT * 2);
    }
}
