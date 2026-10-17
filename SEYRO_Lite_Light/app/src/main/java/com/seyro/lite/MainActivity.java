package com.seyro.lite;

import android.app.Activity;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;

public class MainActivity extends Activity {
    private final int bg=0xfff3f7ff, white=0xffffffff, ink=0xff14233b, muted=0xff738198,
        line=0xffe2eaf6, blue=0xff2864e8, paleBlue=0xffeaf2ff, green=0xff118568;
    private TextView connect,status,hint,profileLinkValue,subscriptionState,protocolBadge,userGreeting,subscriptionExpiryText,trafficSummary,trafficPercent,qualityIndicator,shieldIcon;
    private ObjectAnimator shieldPulse;
    private View trafficProgressFill;
    private LinearLayout trafficBarTrack;
    private volatile String subscriptionTitle="", subscriptionAnnounce="";
    private volatile boolean subscriptionInactive=false;
    private volatile long trafficUpload=-1, trafficDownload=-1, trafficTotal=-1, subscriptionExpire=-1;
    private String activeTransport="";
    private SharedPreferences prefs;
    private String profileLink="";
    private boolean demoOn=false;
    private String selectedConfig="";
    private java.util.ArrayList<ProfileOption> availableProfiles=new java.util.ArrayList<>();
    // Auto mode keeps the remaining subscription profiles as startup-failure fallbacks.
    private java.util.ArrayList<ProfileOption> autoFallbackProfiles=new java.util.ArrayList<>();
    private int autoFallbackIndex=0;
    private static final String[] AUTO_TRANSPORT_PRIORITY = {"xhttp", "tcp", "grpc"};
    private static final int AUTO_MAX_ATTEMPTS = 4;
    private boolean handlingAutoFallback=false;
    private static class ProfileOption {
        final String label, transport, config;
        ProfileOption(String label,String transport,String config) { this.label=label; this.transport=transport; this.config=config; }
    }
    private static final int VPN_PERMISSION_REQUEST=71;
    private static final String APP_VERSION=BuildConfig.VERSION_NAME;
    private boolean vpnStateReceiverRegistered=false;
    private AlertDialog settingsDialog;
    private final BroadcastReceiver vpnStateReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            renderVpnState();
            handleAutoSelectionState();
        }
    };

    private GradientDrawable shape(int color,int radius,int stroke) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius);
        if(stroke!=0)d.setStroke(1,stroke); return d;
    }
    private TextView text(String s,int size,int color,boolean bold) {
        TextView v=new TextView(this); v.setText(s); v.setTextSize(size * 0.95f); v.setTextColor(color);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setIncludeFontPadding(true); return v;
    }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(1); return l; }
    private void padding(View v,int l,int t,int r,int b) {
        v.setPadding(Math.round(l*0.95f),Math.round(t*0.95f),Math.round(r*0.95f),Math.round(b*0.95f));
    }
    private int dp(float value) { return (int)(value * 0.95f * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout card() {
        LinearLayout l=column(); padding(l,16,15,16,15);
        l.setBackground(shape(white,20,line)); return l;
    }
    private void add(LinearLayout parent,View child,int top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=top; parent.addView(child,p);
    }
    private TextView pill(String label,int color,int fill) {
        TextView v=text(label,10,color,true); padding(v,10,6,10,6); v.setGravity(Gravity.CENTER);
        v.setBackground(shape(fill,40,0)); return v;
    }

    @Override public void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        prefs=getSharedPreferences("seyro_profile",MODE_PRIVATE);
        profileLink=prefs.getString("subscription_link","");
        activeTransport=prefs.getString("selected_transport","");
        loadCachedSubscriptionMetadata();
        DebugLog.i(this,"APP","Запуск приложения; VPN state="+SeyroVpnService.vpnState);
        Window w=getWindow();
        w.setStatusBarColor(0xfff3f7ff); w.setNavigationBarColor(0xfff3f7ff);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setClipChildren(false);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xffeaf4ff,0xfff8fbff,0xffe6f0ff}));
        LinearLayout root=column(); root.setClipChildren(false); root.setClipToPadding(false); padding(root,dp(18),dp(38),dp(18),dp(28)); root.setPadding(root.getPaddingLeft(),root.getPaddingTop()+Math.round(getResources().getDisplayMetrics().heightPixels*0.02f),root.getPaddingRight(),root.getPaddingBottom()); scroll.addView(root);

        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=text("S",27,white,true); mark.setGravity(Gravity.CENTER); mark.setBackground(shape(0xff087cff,dp(14),0));
        mark.setElevation(dp(3)); header.addView(mark,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout brand=column(); TextView name=text("SEYRO",23,ink,true); name.setLetterSpacing(.07f); brand.addView(name);
        TextView tagline=text("LITE VPN",9,blue,true); tagline.setLetterSpacing(.18f); brand.addView(tagline);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1); bp.leftMargin=11; header.addView(brand,bp);
        TextView settings=text("⚙",28,blue,true); settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("Настройки");
        settings.setBackground(shape(paleBlue,16,blue));
        settings.setElevation(2f);
        LinearLayout.LayoutParams settingsP=new LinearLayout.LayoutParams(dp(48),dp(48));
        settingsP.leftMargin=dp(8);
        header.addView(settings,settingsP); settings.setOnClickListener(v->showSettings());
        root.addView(header);

        add(root,text("ВАШ ЛИЧНЫЙ VPN",10,blue,true),28);
        userGreeting=text((profileLink.isEmpty()?"Добро пожаловать!":greetingText()),25,ink,true);
        userGreeting.setLineSpacing(0f,1.02f);
        userGreeting.setGravity(Gravity.START);
        add(root,userGreeting,6);
        subscriptionExpiryText=text("Подписка активна до: проверяем…",13,green,true);
        subscriptionExpiryText.setGravity(Gravity.START);
        add(root,subscriptionExpiryText,dp(7));
        // Keep the greeting left-aligned while it fits on one line. If a long
        // username makes it wrap, center both the greeting and VIP/expiry line.
        userGreeting.addOnLayoutChangeListener((v,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom) -> {
            boolean multiline=userGreeting.getLineCount()>1;
            int gravity=multiline?Gravity.CENTER:Gravity.START;
            if(userGreeting.getGravity()!=gravity) userGreeting.setGravity(gravity);
            if(subscriptionExpiryText!=null && subscriptionExpiryText.getGravity()!=gravity)
                subscriptionExpiryText.setGravity(gravity);
        });
        LinearLayout hero=column(); hero.setClipChildren(false); hero.setClipToPadding(false); padding(hero,dp(20),dp(22),dp(20),dp(20));
        GradientDrawable heroGradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xffffffff,0xfff3faff,0xffeaf7ff});
        heroGradient.setCornerRadius(dp(26)); heroGradient.setStroke(dp(1),0xffdcecff);
        hero.setBackground(heroGradient); hero.setElevation(dp(4));
        add(root,hero,dp(12));
        LinearLayout center=column(); center.setGravity(Gravity.CENTER);
        center.setClipChildren(false); center.setClipToPadding(false);
        shieldIcon=text("S",36,white,true); shieldIcon.setGravity(Gravity.CENTER);
        GradientDrawable shieldGradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff10baff,0xff087cff,0xff0755e8});
        shieldGradient.setShape(GradientDrawable.OVAL);
        shieldIcon.setBackground(shieldGradient); shieldIcon.setElevation(dp(7));
        LinearLayout.LayoutParams shieldP=new LinearLayout.LayoutParams(dp(94),dp(94)); shieldP.gravity=Gravity.CENTER;
        center.addView(shieldIcon,shieldP);
        status=text("●  НЕ ПОДКЛЮЧЕНО",12,muted,true); status.setGravity(Gravity.CENTER);
        add(center,status,dp(12)); hero.addView(center);
        qualityIndicator=pill("КАЧЕСТВО СОЕДИНЕНИЯ · ОЖИДАНИЕ ПРОВЕРКИ",muted,0xfff0f4fa);
        LinearLayout.LayoutParams qualityP=new LinearLayout.LayoutParams(-2,-2);
        qualityP.gravity=Gravity.CENTER; qualityP.topMargin=dp(6); center.addView(qualityIndicator,qualityP);
        protocolBadge=pill("ПРОТОКОЛ НЕ ВЫБРАН",blue,paleBlue);
        LinearLayout.LayoutParams protocolP=new LinearLayout.LayoutParams(-2,-2); protocolP.gravity=Gravity.CENTER; protocolP.topMargin=dp(7); center.addView(protocolBadge,protocolP);
        hint=text(profileLink.isEmpty()?"Добавьте ссылку подписки, чтобы настроить защищённое подключение.":"Профиль готов к подключению.",13,muted,false);
        hint.setGravity(Gravity.CENTER); add(hero,hint,8);
        connect=text(profileLink.isEmpty()?"Добавить ссылку подписки":"Подключить",17,white,true); connect.setGravity(Gravity.CENTER);
        connect.setCompoundDrawablePadding(dp(10));
        padding(connect,dp(16),dp(15),dp(16),dp(15));
        GradientDrawable connectGradient=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xff167cff,0xff0860f2,0xff0757e8});
        connectGradient.setCornerRadius(dp(17)); connect.setBackground(connectGradient);
        connect.setElevation(dp(4)); add(hero,connect,dp(18));
        connect.setOnClickListener(v->onConnect());

        LinearLayout trafficCard=column();
        padding(trafficCard,dp(14),dp(12),dp(14),dp(12));
        GradientDrawable trafficGradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xfff8fcff,0xffedf6ff});
        trafficGradient.setCornerRadius(dp(16)); trafficGradient.setStroke(dp(1),0xffdceaff);
        trafficCard.setBackground(trafficGradient);
        add(hero,trafficCard,dp(12));
        LinearLayout trafficHeader=new LinearLayout(this);
        trafficHeader.setGravity(Gravity.CENTER_VERTICAL);
        trafficHeader.addView(text("ТРАФИК ПОДПИСКИ",11,muted,true),
            new LinearLayout.LayoutParams(0,-2,1));
        trafficPercent=text("—",13,blue,true);
        trafficHeader.addView(trafficPercent);
        trafficCard.addView(trafficHeader);
        trafficSummary=text("Загрузка данных о трафике…",12,muted,false);
        add(trafficCard,trafficSummary,dp(5));
        trafficBarTrack=new LinearLayout(this);
        trafficBarTrack.setBackground(shape(0xffe2eaf6,dp(20),0));
        trafficBarTrack.setClipToOutline(true);
        trafficProgressFill=new View(this);
        trafficProgressFill.setBackground(shape(blue,dp(20),0));
        trafficBarTrack.addView(trafficProgressFill,new LinearLayout.LayoutParams(0,dp(7)));
        LinearLayout.LayoutParams trackP=new LinearLayout.LayoutParams(-1,dp(7));
        trackP.topMargin=dp(9);
        trafficCard.addView(trafficBarTrack,trackP);
        updateSubscriptionWidgets();

        LinearLayout help=card(); help.setElevation(dp(2)); add(root,help,dp(14));
        help.setOnClickListener(v->showHelp());
        help.addView(text("НУЖНА ПОМОЩЬ?",10,muted,true));
        add(help,text("Поддержка  ›",14,blue,true),7);

        TextView footer=text("SEYRO LITE  ·  ВЕРСИЯ "+APP_VERSION,10,0xff93a0b5,true);
        footer.setGravity(Gravity.CENTER); add(root,footer,20);
        setContentView(scroll);
        renderVpnState();
        if(!profileLink.isEmpty()) refreshSubscriptionMetadataInBackground();
        checkForAppUpdate(false);
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter=new IntentFilter(SeyroVpnService.ACTION_STATE_CHANGED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(vpnStateReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(vpnStateReceiver,filter);
        vpnStateReceiverRegistered=true;
        renderVpnState();
    }

    @Override protected void onResume() {
        super.onResume();
        SeyroVpnService.requestImmediateProbe("возврат в приложение / разблокировка экрана");
        if (pendingInstallAfterPermission) {
            pendingInstallAfterPermission = false;
            java.io.File apk = new java.io.File(getCacheDir(), "updates/seyro-lite-update.apk");
            if (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls()) installDownloadedUpdate(apk);
        }
    }

    @Override protected void onStop() {
        if(vpnStateReceiverRegistered) { unregisterReceiver(vpnStateReceiver); vpnStateReceiverRegistered=false; }
        super.onStop();
    }

    private void renderVpnState() {
        if(status==null||connect==null||hint==null) return;
        String state=SeyroVpnService.vpnState;
        updateConnectionQuality();
        if("connected".equals(state)) startShieldPulse(); else stopShieldPulse();
        if("connected".equals(state)) {
            status.setText("●  ПОДКЛЮЧЕНО"); status.setTextColor(green);
            connect.setText("Отключиться"); connect.setEnabled(true);
            hint.setText("Защищённое соединение активно.");
            updateProtocolBadge();
        } else if("connecting".equals(state)||"disconnecting".equals(state)) {
            status.setText("●  "+("connecting".equals(state)?"ПОДКЛЮЧАЕМСЯ":"ОТКЛЮЧАЕМСЯ")); status.setTextColor(blue);
            connect.setText("Подождите…"); connect.setEnabled(false);
            hint.setText("Запускаем защищённый туннель. Состояние обновится автоматически.");
        } else if("error".equals(state)) {
            status.setText("●  НЕ УДАЛОСЬ ПОДКЛЮЧИТЬ"); status.setTextColor(0xffc64747);
            connect.setText("Повторить"); connect.setEnabled(true);
            hint.setText(SeyroVpnService.lastError.isEmpty()?"Проверьте профиль и попробуйте снова.":SeyroVpnService.lastError);
        } else {
            status.setText("●  НЕ ПОДКЛЮЧЕНО"); status.setTextColor(muted);
            connect.setText("Подключить"); connect.setEnabled(true);
            hint.setText(profileLink.isEmpty()?"Вставьте ссылку подписки из личного кабинета SEYRO.":"Профиль готов к подключению.");
            connect.setText(profileLink.isEmpty()?"Добавить ссылку подписки":"Подключить");
            updateProtocolBadge();
        }
        if("connecting".equals(state)||"disconnecting".equals(state)) connect.setCompoundDrawables(null,null,null,null);
        else connect.setCompoundDrawables(new PowerGlyph(white,dp(19)),null,null,null);
        updateProfileSubscriptionState();
        updateSubscriptionWidgets();
    }

    private final class PowerGlyph extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final float size;
        PowerGlyph(int color,int size) { this.color=color; this.size=size; setBounds(0,0,size,size); }
        @Override public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b=getBounds();
            float cx=b.exactCenterX(), cy=b.exactCenterY();
            float radius=size*0.32f;
            paint.setColor(color); paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(size*0.105f); paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            android.graphics.RectF arc=new android.graphics.RectF(cx-radius,cy-radius,cx+radius,cy+radius);
            canvas.drawArc(arc,135,270,false,paint);
            canvas.drawLine(cx,cy-radius-size*0.10f,cx,cy+radius*0.12f,paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private void updateConnectionQuality() {
        if(qualityIndicator==null) return;
        long ms=SeyroVpnService.lastProbeDelayMs;
        if(!"connected".equals(SeyroVpnService.vpnState)) {
            qualityIndicator.setText("КАЧЕСТВО СОЕДИНЕНИЯ · НЕ ПОДКЛЮЧЕНО");
            qualityIndicator.setTextColor(muted);
            qualityIndicator.setBackground(shape(0xfff0f4fa,dp(40),0));
        } else if(ms<0) {
            qualityIndicator.setText("КАЧЕСТВО СОЕДИНЕНИЯ · ПРОВЕРЯЕМ…");
            qualityIndicator.setTextColor(blue);
            qualityIndicator.setBackground(shape(paleBlue,dp(40),0));
        } else if(ms<100) {
            qualityIndicator.setText("● ОТЛИЧНОЕ СОЕДИНЕНИЕ · "+ms+" мс");
            qualityIndicator.setTextColor(green);
            qualityIndicator.setBackground(shape(0xffe4f8f0,dp(40),0));
        } else if(ms<200) {
            qualityIndicator.setText("● ХОРОШЕЕ СОЕДИНЕНИЕ · "+ms+" мс");
            qualityIndicator.setTextColor(0xff9a6a00);
            qualityIndicator.setBackground(shape(0xfffff4d8,dp(40),0));
        } else {
            qualityIndicator.setText("● ВЫСОКАЯ ЗАДЕРЖКА · "+ms+" мс");
            qualityIndicator.setTextColor(0xffbd4141);
            qualityIndicator.setBackground(shape(0xffffe9e9,dp(40),0));
        }
    }

    private void startShieldPulse() {
        if(shieldIcon==null || (shieldPulse!=null && shieldPulse.isRunning())) return;
        shieldPulse=ObjectAnimator.ofFloat(shieldIcon,"scaleX",1f,1.06f,1f);
        shieldPulse.setDuration(1600);
        shieldPulse.setRepeatCount(ValueAnimator.INFINITE);
        shieldPulse.setRepeatMode(ValueAnimator.RESTART);
        ObjectAnimator pulseY=ObjectAnimator.ofFloat(shieldIcon,"scaleY",1f,1.06f,1f);
        pulseY.setDuration(1600);
        pulseY.setRepeatCount(ValueAnimator.INFINITE);
        pulseY.setRepeatMode(ValueAnimator.RESTART);
        shieldPulse.start();
        pulseY.start();
        shieldIcon.setTag(pulseY);
    }

    private void stopShieldPulse() {
        if(shieldPulse!=null) { shieldPulse.cancel(); shieldPulse=null; }
        if(shieldIcon!=null && shieldIcon.getTag() instanceof ObjectAnimator) {
            ((ObjectAnimator)shieldIcon.getTag()).cancel();
            shieldIcon.setTag(null);
            shieldIcon.setScaleX(1f); shieldIcon.setScaleY(1f);
        }
    }

    private void updateProtocolBadge() {
        if(protocolBadge==null) return;
        String state=SeyroVpnService.vpnState;
        String mode=prefs.getString("selection_mode","auto");
        String modeLabel="auto".equals(mode)?"АВТОВЫБОР":"РУЧНОЙ ВЫБОР";
        String transport=activeTransport;
        if(transport.isEmpty() && !selectedConfig.isEmpty()) transport=detectTransport(selectedConfig);
        String protocol=transport.isEmpty()?"ПРОТОКОЛ НЕ ВЫБРАН":"VLESS + REALITY  ·  "+transport.toUpperCase(java.util.Locale.ROOT);
        if("connecting".equals(state)) protocolBadge.setText(modeLabel+"  ·  ПОДКЛЮЧЕНИЕ…");
        else protocolBadge.setText(modeLabel+"  ·  "+protocol);
    }

    private View menuRow(String title,String subtitle,String icon,Runnable action) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); padding(row,0,13,0,13);
        LinearLayout words=column(); words.addView(text(title,14,ink,true));
        add(words,text(subtitle,11,muted,false),3);
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text(icon,20,muted,true); arrow.setGravity(Gravity.CENTER);
        row.addView(arrow,new LinearLayout.LayoutParams(30,40));
        row.setOnClickListener(v->action.run()); return row;
    }
    private void divider(LinearLayout parent) {
        View d=new View(this); d.setBackgroundColor(line); parent.addView(d,new LinearLayout.LayoutParams(-1,1));
    }
    private void showProfile() {
        final boolean hasProfile=!profileLink.trim().isEmpty();
        LinearLayout box=column();
        padding(box,dp(20),dp(18),dp(20),dp(18));
        box.setBackground(shape(white,dp(26),0));
        TextView icon=text(hasProfile?"✓":"＋",26,white,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(hasProfile?green:blue,dp(18),0));
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(dp(56),dp(56));
        iconP.gravity=Gravity.CENTER_HORIZONTAL;
        box.addView(icon,iconP);

        TextView title=text(hasProfile?"Профиль добавлен":"Добавьте профиль SEYRO",18,ink,true);
        title.setGravity(Gravity.CENTER);
        add(box,title,dp(12));
        TextView description=text(hasProfile
            ?"Ссылка подписки сохранена на этом устройстве. При необходимости её можно заменить."
            :"Вставьте постоянную ссылку подписки из личного кабинета SEYRO. Ссылка хранится только на этом устройстве.",12,muted,false);
        description.setGravity(Gravity.CENTER);
        add(box,description,dp(6));

        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setText(profileLink);
        input.setHint("https://… или vless://…");
        input.setTextSize(14);
        input.setSelectAllOnFocus(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        input.setPadding(dp(14),dp(12),dp(14),dp(12));
        input.setBackground(shape(0xfff5f8ff,dp(14),line));
        add(box,input,dp(18));

        TextView paste=text("▣   Вставить из буфера обмена",14,blue,true);
        paste.setGravity(Gravity.CENTER);
        paste.setPadding(dp(12),dp(12),dp(12),dp(12));
        paste.setBackground(shape(paleBlue,dp(14),0));
        add(box,paste,dp(10));
        paste.setOnClickListener(v->{
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clipboard!=null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount()>0) {
                CharSequence clip=clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                if(clip!=null && clip.length()>0) {
                    input.setText(clip.toString().trim());
                    input.setSelection(input.length());
                    Toast.makeText(this,"Ссылка вставлена",Toast.LENGTH_SHORT).show();
                } else Toast.makeText(this,"Буфер обмена пуст",Toast.LENGTH_SHORT).show();
            } else Toast.makeText(this,"Буфер обмена пуст",Toast.LENGTH_SHORT).show();
        });

        if(hasProfile) {
            TextView remove=text("Удалить сохранённую ссылку",13,0xffc13e4b,true);
            remove.setGravity(Gravity.CENTER);
            remove.setPadding(dp(12),dp(13),dp(12),dp(10));
            add(box,remove,dp(8));
            remove.setOnClickListener(v->{
                LinearLayout confirmBox=column();
                padding(confirmBox,dp(20),dp(20),dp(20),dp(16));
                confirmBox.setBackground(shape(white,dp(24),0));

                TextView confirmTitle=text("Удалить профиль?",19,ink,true);
                add(confirmBox,confirmTitle,dp(2));

                TextView message=text(
                    "Сохранённая ссылка будет удалена с этого устройства. Для подключения потребуется добавить её снова.",
                    14,muted,false);
                message.setLineSpacing(dp(3),1.0f);
                add(confirmBox,message,dp(10));

                LinearLayout confirmActions=new LinearLayout(this);
                confirmActions.setOrientation(LinearLayout.HORIZONTAL);
                confirmActions.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams confirmActionsP=new LinearLayout.LayoutParams(-1,-2);
                confirmActionsP.topMargin=dp(16);
                confirmBox.addView(confirmActions,confirmActionsP);

                TextView keep=text("Оставить",14,blue,true);
                keep.setGravity(Gravity.CENTER);
                keep.setPadding(dp(8),dp(12),dp(8),dp(12));
                keep.setBackground(shape(paleBlue,dp(14),0));
                LinearLayout.LayoutParams keepP=new LinearLayout.LayoutParams(0,-2,1);
                keepP.rightMargin=dp(6);
                confirmActions.addView(keep,keepP);

                TextView delete=text("Удалить",14,0xffb4232f,true);
                delete.setGravity(Gravity.CENTER);
                delete.setPadding(dp(8),dp(12),dp(8),dp(12));
                delete.setBackground(shape(0xffffe8e9,dp(14),0));
                LinearLayout.LayoutParams deleteP=new LinearLayout.LayoutParams(0,-2,1);
                deleteP.leftMargin=dp(6);
                confirmActions.addView(delete,deleteP);

                AlertDialog confirmDialog=new AlertDialog.Builder(this).setView(confirmBox).create();
                confirmDialog.setOnShowListener(ignored->styleCustomDialog(confirmDialog,0.58f));
                keep.setOnClickListener(ignored->confirmDialog.dismiss());
                delete.setOnClickListener(ignored->{
                    confirmDialog.dismiss();
                    profileLink="";
                    clearCachedSubscriptionMetadata();
                    selectedConfig="";
                    activeTransport="";
                    availableProfiles.clear();
                    prefs.edit().remove("subscription_link").remove("selected_transport").apply();
                    DebugLog.i(this,"PROFILE","Ссылка подписки удалена");
                    refreshProfileState();
                });
                confirmDialog.show();
            });
        }

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsP=new LinearLayout.LayoutParams(-1,-2);
        actionsP.topMargin=dp(12);
        box.addView(actions,actionsP);

        TextView cancel=text("Отмена",14,blue,true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(10),dp(12),dp(10),dp(12));
        cancel.setBackground(shape(paleBlue,dp(14),line));
        LinearLayout.LayoutParams cancelP=new LinearLayout.LayoutParams(0,-2,1);
        cancelP.rightMargin=dp(6);
        actions.addView(cancel,cancelP);

        TextView save=text("✓   Сохранить",14,blue,true);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(10),dp(12),dp(10),dp(12));
        save.setBackground(shape(paleBlue,dp(14),0));
        LinearLayout.LayoutParams saveP=new LinearLayout.LayoutParams(0,-2,1);
        saveP.leftMargin=dp(6);
        actions.addView(save,saveP);

        AlertDialog dialog=new AlertDialog.Builder(this)
            .setView(box)
            .create();
        dialog.setOnShowListener(ignored->{
            Window window=dialog.getWindow();
            if(window!=null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams params=window.getAttributes();
                params.width=getResources().getDisplayMetrics().widthPixels-dp(32);
                params.height=WindowManager.LayoutParams.WRAP_CONTENT;
                params.gravity=Gravity.CENTER;
                params.dimAmount=0.58f;
                window.setAttributes(params);
            }
        });
        cancel.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{
            String value=input.getText().toString().trim();
            if(value.isEmpty()) {
                input.setError("Вставьте ссылку подписки");
                return;
            }
            if(!validLink(value)) {
                input.setError("Нужна ссылка HTTPS, HTTP или VLESS");
                return;
            }
            boolean changed=!value.equals(profileLink);
            profileLink=value;
            prefs.edit().putString("subscription_link",profileLink).apply();
            if(changed) {
                clearCachedSubscriptionMetadata();
                selectedConfig="";
                activeTransport="";
                availableProfiles.clear();
                prefs.edit().remove("selected_transport").apply();
            }
            DebugLog.i(this,"PROFILE","Ссылка подписки сохранена; значение скрыто");
            refreshProfileState();
            dialog.dismiss();
            Toast.makeText(this,changed?"Профиль сохранён":"Ссылка не изменилась",Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    private boolean validLink(String value) {
        String s=value.toLowerCase(java.util.Locale.ROOT);
        return s.startsWith("https://")||s.startsWith("http://")||s.startsWith("vless://");
    }
    private void refreshProfileState() {
        if(userGreeting!=null) userGreeting.setText(greetingText());
        updateProfileSubscriptionState();
        updateSubscriptionWidgets();
        renderVpnState();
        if(!profileLink.isEmpty()) refreshSubscriptionMetadataInBackground();
    }
    private void showSettings() {
        String mode=prefs.getString("selection_mode","auto");
        boolean manual="manual".equals(mode);

        LinearLayout box=column();
        padding(box,dp(20),dp(20),dp(20),dp(16));
        box.setBackground(shape(white,dp(26),0));

        TextView icon=text("⚙",25,white,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(blue,dp(18),0));
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(dp(54),dp(54));
        iconP.gravity=Gravity.CENTER_HORIZONTAL;
        box.addView(icon,iconP);

        TextView title=text("Настройки подключения",19,ink,true);
        title.setGravity(Gravity.CENTER);
        add(box,title,dp(12));
        TextView subtitle=text("Выберите режим работы VPN",12,muted,false);
        subtitle.setGravity(Gravity.CENTER);
        add(box,subtitle,dp(5));

        LinearLayout options=column();
        add(box,options,dp(16));
        LinearLayout autoOption=addSettingsOption(options,"Автоматический выбор",
            "Подключаться к первому доступному профилю","auto".equals(mode),
            ()->changeSelectionMode("auto"));
        autoOption.setTag("mode_auto");
        LinearLayout manualOption=addSettingsOption(options,"Ручной выбор",
            "Самостоятельно выбирать транспорт TCP или XHTTP",manual,
            ()->changeSelectionMode("manual"));
        manualOption.setTag("mode_manual");

        TextView section=text("ПРОФИЛЬ ПОДКЛЮЧЕНИЯ",10,muted,true);
        section.setTag("profile_section");
        add(options,section,dp(12));
        LinearLayout choose=rowCard("Выбрать профиль подключения",
            "Текущий выбор: "+(activeTransport.isEmpty()?"не выбран":activeTransport.toUpperCase(java.util.Locale.ROOT)),
            "›",false);
        choose.setTag("profile_choice");
        add(options,choose,dp(6));
        choose.setOnClickListener(v->showProfileChoices());
        section.setVisibility(manual?View.VISIBLE:View.GONE);
        choose.setVisibility(manual?View.VISIBLE:View.GONE);

        // Не показываем обновление подписки, пока пользователь не добавил ссылку.
        if(!profileLink.isEmpty()) {
            LinearLayout refresh=rowCard("Обновить подписку",
                "Обновить сведения о сроке и трафике","↻",false);
            add(options,refresh,dp(8));
            refresh.setOnClickListener(v->{
                refreshSubscriptionMetadataInBackground(true);
                showSimpleStyledNotice("Обновление подписки",
                    "Запрошено обновление данных подписки.");
            });
        }

        LinearLayout appUpdate=rowCard("Обновить приложение",
            "Проверить наличие новой версии SEYRO Lite","⬇",false);
        add(options,appUpdate,dp(8));
        appUpdate.setOnClickListener(v->{
            if(settingsDialog!=null) settingsDialog.dismiss();
            checkForAppUpdate(true);
        });

        LinearLayout debug=rowCard("Журнал отладки",
            "Диагностика проблем с подключением","≡",false);
        add(options,debug,dp(8));
        debug.setOnClickListener(v->showDebugLog());

        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsP=new LinearLayout.LayoutParams(-1,-2);
        actionsP.topMargin=dp(16);
        box.addView(actions,actionsP);
        TextView close=text("Закрыть",14,blue,true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(shape(paleBlue,dp(14),0));
        padding(close,dp(10),dp(12),dp(10),dp(12));
        actions.addView(close,new LinearLayout.LayoutParams(0,-2,1));
        TextView profile=text("Ссылка профиля",14,white,true);
        profile.setGravity(Gravity.CENTER);
        profile.setBackground(shape(blue,dp(14),0));
        padding(profile,dp(10),dp(12),dp(10),dp(12));
        LinearLayout.LayoutParams profileP=new LinearLayout.LayoutParams(0,-2,1);
        profileP.leftMargin=dp(8);
        actions.addView(profile,profileP);

        AlertDialog dialog=new AlertDialog.Builder(this).setView(box).create();
        settingsDialog=dialog;
        dialog.setOnShowListener(ignored->styleCustomDialog(dialog,0.58f));
        dialog.setOnDismissListener(ignored->{if(settingsDialog==dialog)settingsDialog=null;});
        close.setOnClickListener(v->dialog.dismiss());
        profile.setOnClickListener(v->{dialog.dismiss();showProfile();});
        dialog.show();
    }

    private LinearLayout addSettingsOption(LinearLayout parent,String titleText,String description,
                                   boolean selected,Runnable action) {
        LinearLayout item=column();
        padding(item,dp(13),dp(11),dp(13),dp(11));
        item.setBackground(shape(selected?paleBlue:0xfff7faff,dp(16),selected?blue:line));
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(titleText,14,ink,true);
        row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView check=text(selected?"✓":"○",20,selected?blue:muted,true);
        check.setGravity(Gravity.CENTER);
        row.addView(check,new LinearLayout.LayoutParams(dp(30),-2));
        item.addView(row);
        TextView desc=text(description,11,muted,false);
        add(item,desc,dp(3));
        add(parent,item,dp(7));
        item.setOnClickListener(v->action.run());
        return item;
    }

    private LinearLayout rowCard(String titleText,String description,String symbol,boolean selected) {
        LinearLayout item=column();
        padding(item,dp(13),dp(11),dp(13),dp(11));
        item.setBackground(shape(selected?paleBlue:0xfff7faff,dp(15),selected?blue:line));
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text(titleText,13,ink,true);
        row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView marker=text(symbol,20,blue,true);
        marker.setGravity(Gravity.CENTER);
        row.addView(marker,new LinearLayout.LayoutParams(dp(28),-2));
        item.addView(row);
        add(item,text(description,11,muted,false),dp(3));
        return item;
    }

    private void updateSettingsModeViews(View root,String mode) {
        if(root==null) return;
        updateModeCard(root.findViewWithTag("mode_auto"),"auto".equals(mode));
        updateModeCard(root.findViewWithTag("mode_manual"),"manual".equals(mode));
        View section=root.findViewWithTag("profile_section");
        View choice=root.findViewWithTag("profile_choice");
        int visibility="manual".equals(mode)?View.VISIBLE:View.GONE;
        if(section!=null) section.setVisibility(visibility);
        if(choice!=null) {
            choice.setVisibility(visibility);
            if(choice instanceof LinearLayout) {
                LinearLayout card=(LinearLayout)choice;
                if(card.getChildCount()>0 && card.getChildAt(0) instanceof LinearLayout) {
                    LinearLayout row=(LinearLayout)card.getChildAt(0);
                    if(row.getChildCount()>0 && row.getChildAt(0) instanceof TextView) {
                        // The detail line is kept in sync with the currently selected transport.
                        if(card.getChildCount()>1 && card.getChildAt(1) instanceof TextView) {
                            ((TextView)card.getChildAt(1)).setText("Текущий выбор: "+
                                (activeTransport.isEmpty()?"не выбран":activeTransport.toUpperCase(java.util.Locale.ROOT)));
                        }
                    }
                }
            }
        }
    }

    private void updateModeCard(View view,boolean selected) {
        if(!(view instanceof LinearLayout)) return;
        LinearLayout card=(LinearLayout)view;
        card.setBackground(shape(selected?paleBlue:0xfff7faff,dp(16),selected?blue:line));
        if(card.getChildCount()>0 && card.getChildAt(0) instanceof LinearLayout) {
            LinearLayout row=(LinearLayout)card.getChildAt(0);
            for(int i=0;i<row.getChildCount();i++) {
                View child=row.getChildAt(i);
                if(child instanceof TextView && i==1) {
                    ((TextView)child).setText(selected?"✓":"○");
                    ((TextView)child).setTextColor(selected?blue:muted);
                }
            }
        }
    }

    private void changeSelectionMode(String next) {
        String old = prefs.getString("selection_mode", "auto");
        boolean changed = !next.equals(old);
        prefs.edit().putString("selection_mode", next).apply();
        updateProtocolBadge();

        if (changed) {
            activeTransport = "";
            selectedConfig = "";
            prefs.edit().remove("selected_transport").apply();
            autoFallbackProfiles.clear();
            autoFallbackIndex = 0;
            DebugLog.i(this, "PROFILE",
                "Смена режима: " + old + " → " + next + "; activeTransport сброшен");
        }

        if (changed && "connected".equals(SeyroVpnService.vpnState)) {
            DebugLog.i(this, "VPN", "Переподключение для применения режима " + next);
            Intent stop = new Intent(this, SeyroVpnService.class);
            stop.setAction(SeyroVpnService.ACTION_STOP);
            startService(stop);
            connect.postDelayed(() -> onConnect(), 1000);
        }

        if (settingsDialog != null && settingsDialog.isShowing()) {
            View root = settingsDialog.findViewById(android.R.id.content);
            updateSettingsModeViews(root, next);
        }
    }

    private void styleCustomDialog(AlertDialog dialog,float dimAmount) {
        Window window=dialog.getWindow();
        if(window!=null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            // Remove Android's default dialog enter/exit animation: custom SEYRO cards
            // should appear without the visible "window popping" effect.
            window.setWindowAnimations(0);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params=window.getAttributes();
            params.width=getResources().getDisplayMetrics().widthPixels-dp(32);
            params.height=WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity=Gravity.CENTER;
            params.dimAmount=dimAmount;
            window.setAttributes(params);
        }
    }

    private void showStyledManualPrompt() {
        LinearLayout box=column();
        padding(box,dp(20),dp(20),dp(20),dp(16));
        box.setBackground(shape(white,dp(24),0));
        TextView icon=text("!",24,white,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(blue,dp(18),0));
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(dp(52),dp(52));
        iconP.gravity=Gravity.CENTER_HORIZONTAL;
        box.addView(icon,iconP);
        TextView title=text("Ручной выбор",19,ink,true);
        title.setGravity(Gravity.CENTER);
        add(box,title,dp(12));
        TextView description=text("Сначала выберите профиль подключения. После выбора нажмите «Подключить».",13,muted,false);
        description.setGravity(Gravity.CENTER);
        add(box,description,dp(7));
        LinearLayout buttons=new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buttonsP=new LinearLayout.LayoutParams(-1,-2);
        buttonsP.topMargin=dp(18);
        box.addView(buttons,buttonsP);
        TextView cancel=text("Отмена",14,blue,true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(shape(paleBlue,dp(14),0));
        padding(cancel,dp(8),dp(12),dp(8),dp(12));
        buttons.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));
        TextView open=text("Выбрать профиль",14,white,true);
        open.setGravity(Gravity.CENTER);
        open.setBackground(shape(blue,dp(14),0));
        padding(open,dp(8),dp(12),dp(8),dp(12));
        LinearLayout.LayoutParams openP=new LinearLayout.LayoutParams(0,-2,1);
        openP.leftMargin=dp(8);
        buttons.addView(open,openP);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(box).create();
        dialog.setOnShowListener(ignored->styleCustomDialog(dialog,0.58f));
        cancel.setOnClickListener(v->dialog.dismiss());
        open.setOnClickListener(v->{dialog.dismiss();showSettings();});
        dialog.show();
    }

    private void showProfileChoices() {
        if(!"disconnected".equals(SeyroVpnService.vpnState)) {
            showSimpleStyledNotice("Сначала отключите VPN",
                "Выбор транспорта доступен после отключения.");
            return;
        }
        if(profileLink.isEmpty()) {
            showProfile();
            return;
        }
        // Always fetch the current subscription list when the user opens the picker,
        // so newly added server profiles appear without restarting the app. No toast.
        loadProfilesForManualChoice();
    }

    private void loadProfilesForManualChoice() {
        if(profileLink.isEmpty()) { showProfile(); return; }
        new Thread(() -> {
            java.util.ArrayList<ProfileOption> result=null;
            String problem=null;
            try { result=fetchAndBuildConfigs(profileLink); }
            catch(Exception e) { problem=e.getMessage()==null?"Не удалось загрузить профили":e.getMessage(); }
            final java.util.ArrayList<ProfileOption> loaded=result;
            final String error=problem;
            runOnUiThread(() -> {
                if(error!=null || loaded==null || loaded.isEmpty()) {
                    showStyledError("Не удалось загрузить профили",
                        error==null?"Подписка не содержит доступных профилей.":error,
                        ()->loadProfilesForManualChoice());
                    return;
                }
                availableProfiles=loaded;
                showProfilePicker(loaded);
            });
        }).start();
    }

    private void showProfilePicker(java.util.ArrayList<ProfileOption> profiles) {
        LinearLayout box=column();
        padding(box,dp(20),dp(20),dp(20),dp(16));
        box.setBackground(shape(white,dp(26),0));
        TextView icon=text("⇄",25,white,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(blue,dp(18),0));
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(dp(54),dp(54));
        iconP.gravity=Gravity.CENTER_HORIZONTAL;
        box.addView(icon,iconP);
        TextView title=text("Профиль подключения",19,ink,true);
        title.setGravity(Gravity.CENTER);
        add(box,title,dp(12));
        TextView subtitle=text("Выберите транспорт TCP или XHTTP",12,muted,false);
        subtitle.setGravity(Gravity.CENTER);
        add(box,subtitle,dp(5));

        LinearLayout list=column();
        add(box,list,dp(14));
        AlertDialog dialog=new AlertDialog.Builder(this).setView(box).create();
        String persistedTransport=prefs.getString("selected_transport","");
        for(ProfileOption option:profiles) {
            // On a fresh app launch selectedConfig is empty until Connect is pressed.
            // The saved transport is the source of truth for the picker in that case.
            boolean selected=!selectedConfig.isEmpty()
                ? selectedConfig.equals(option.config)
                : (!activeTransport.isEmpty() && activeTransport.equalsIgnoreCase(option.transport))
                    || (!persistedTransport.isEmpty() && persistedTransport.equalsIgnoreCase(option.transport));
            String transport=option.transport.toUpperCase(java.util.Locale.ROOT);
            LinearLayout item=rowCard(option.label,transport+(selected?" · выбран":""),
                selected?"✓":"›",selected);
            add(list,item,dp(7));
            item.setOnClickListener(v->{
                dialog.dismiss();
                selectProfileWithoutStarting(option);
            });
        }
        LinearLayout buttons=new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buttonsP=new LinearLayout.LayoutParams(-1,-2);
        buttonsP.topMargin=dp(16);
        box.addView(buttons,buttonsP);
        TextView cancel=text("Отмена",14,blue,true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(shape(paleBlue,dp(14),0));
        padding(cancel,dp(10),dp(12),dp(10),dp(12));
        buttons.addView(cancel,new LinearLayout.LayoutParams(-1,-2));
        // Match the main-menu modal dim so the profile picker clearly sits above settings.
        dialog.setOnShowListener(ignored->styleCustomDialog(dialog,0.58f));
        cancel.setOnClickListener(v->dialog.dismiss());
        dialog.show();
    }

    private void selectProfileWithoutStarting(ProfileOption option) {
        selectedConfig=option.config;
        activeTransport=option.transport;
        prefs.edit().putString("selected_transport",activeTransport).apply();
        DebugLog.i(this,"PROFILE","Выбран профиль в настройках: "+option.label+"; transport="+option.transport);
        updateProtocolBadge();
        if(hint!=null) hint.setText("Выбран профиль: "+option.label+" ("+option.transport.toUpperCase(java.util.Locale.ROOT)+")");
        // The selected transport is shown in the settings card; no extra toast needed.

        // Reopen the settings card so the selected transport is visible immediately,
        // without an extra confirmation dialog.
        if(settingsDialog!=null && settingsDialog.isShowing()) {
            View root=settingsDialog.findViewById(android.R.id.content);
            View choice=root==null?null:root.findViewWithTag("profile_choice");
            if(choice instanceof LinearLayout && ((LinearLayout)choice).getChildCount()>1
                && ((LinearLayout)choice).getChildAt(1) instanceof TextView) {
                ((TextView)((LinearLayout)choice).getChildAt(1)).setText("Текущий выбор: "+activeTransport.toUpperCase(java.util.Locale.ROOT));
            }
        }
    }

    private void showSimpleStyledNotice(String titleText,String message) {
        LinearLayout box=column();
        padding(box,dp(20),dp(20),dp(20),dp(16));
        box.setBackground(shape(white,dp(24),0));
        TextView icon=text("!",24,white,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(blue,dp(18),0));
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(dp(50),dp(50));
        iconP.gravity=Gravity.CENTER_HORIZONTAL;
        box.addView(icon,iconP);
        TextView title=text(titleText,18,ink,true);
        title.setGravity(Gravity.CENTER);
        add(box,title,dp(12));
        TextView body=text(message,13,muted,false);
        body.setGravity(Gravity.CENTER);
        add(box,body,dp(6));
        TextView ok=text("Понятно",14,white,true);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(shape(blue,dp(14),0));
        padding(ok,dp(12),dp(12),dp(12),dp(12));
        add(box,ok,dp(16));
        AlertDialog dialog=new AlertDialog.Builder(this).setView(box).create();
        dialog.setOnShowListener(ignored->styleCustomDialog(dialog,0.58f));
        ok.setOnClickListener(v->dialog.dismiss());
        dialog.show();
    }

    private void showStyledError(String titleText,String message,Runnable retry) {
        LinearLayout box=column();
        padding(box,dp(20),dp(20),dp(20),dp(16));
        box.setBackground(shape(white,dp(24),0));
        TextView icon=text("!",24,white,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(0xffd94a58,dp(18),0));
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(dp(50),dp(50));
        iconP.gravity=Gravity.CENTER_HORIZONTAL;
        box.addView(icon,iconP);
        TextView title=text(titleText,18,ink,true);
        title.setGravity(Gravity.CENTER);
        add(box,title,dp(12));
        TextView body=text(message,13,muted,false);
        body.setGravity(Gravity.CENTER);
        add(box,body,dp(6));
        LinearLayout buttons=new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);
        bp.topMargin=dp(16);
        box.addView(buttons,bp);
        TextView close=text("Закрыть",14,blue,true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(shape(paleBlue,dp(14),0));
        padding(close,dp(10),dp(12),dp(10),dp(12));
        buttons.addView(close,new LinearLayout.LayoutParams(0,-2,1));
        TextView again=text("Повторить",14,white,true);
        again.setGravity(Gravity.CENTER);
        again.setBackground(shape(blue,dp(14),0));
        padding(again,dp(10),dp(12),dp(10),dp(12));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);
        ap.leftMargin=dp(8);
        buttons.addView(again,ap);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(box).create();
        dialog.setOnShowListener(ignored->styleCustomDialog(dialog,0.58f));
        close.setOnClickListener(v->dialog.dismiss());
        again.setOnClickListener(v->{dialog.dismiss();retry.run();});
        dialog.show();
    }

    private void showDebugLog() {
        String log=DebugLog.read(this);
        if(log.isEmpty()) log="Журнал пока пуст. Повторите подключение, затем откройте этот экран.";
        // Match the settings card width and adapt the log viewport to the current
        // screen height, leaving a stable header/footer in both auto and manual mode.
        int screenHeight=getResources().getDisplayMetrics().heightPixels;
        int logHeight=Math.max(dp(180),Math.min(dp(430),screenHeight-dp(360)));
        LinearLayout box=column();
        padding(box,dp(20),dp(18),dp(20),dp(16));
        box.setBackground(shape(white,dp(26),0));
        TextView icon=text("≡",22,white,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(blue,dp(18),0));
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(dp(48),dp(48));
        iconP.gravity=Gravity.CENTER_HORIZONTAL;
        box.addView(icon,iconP);
        TextView title=text("Журнал отладки",19,ink,true);
        title.setGravity(Gravity.CENTER);
        add(box,title,dp(10));
        TextView subtitle=text(BuildConfig.DEBUG?"Подробные этапы подключения · режим отладки":"Диагностика подключения SEYRO",12,muted,false);
        subtitle.setGravity(Gravity.CENTER);
        add(box,subtitle,dp(8));
        TextView view=text(log,11,ink,false);
        view.setTextIsSelectable(true);
        view.setPadding(dp(12),dp(12),dp(12),dp(12));
        view.setBackground(shape(0xfff5f8ff,dp(14),line));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setPadding(0,dp(12),0,dp(2));
        scroll.addView(view,new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams scrollP=new LinearLayout.LayoutParams(-1,logHeight);
        box.addView(scroll,scrollP);
        LinearLayout buttons=new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buttonsP=new LinearLayout.LayoutParams(-1,-2);
        buttonsP.topMargin=dp(12);
        box.addView(buttons,buttonsP);
        TextView refresh=text("Обновить",12,blue,true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(shape(paleBlue,dp(13),0));
        padding(refresh,dp(6),dp(12),dp(6),dp(12));
        buttons.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));
        TextView share=text("Поделиться",12,blue,true);
        share.setGravity(Gravity.CENTER);
        share.setBackground(shape(paleBlue,dp(13),0));
        padding(share,dp(8),dp(12),dp(8),dp(12));
        LinearLayout.LayoutParams shareP=new LinearLayout.LayoutParams(0,-2,1);
        shareP.leftMargin=dp(5);
        buttons.addView(share,shareP);
        TextView clear=text("Очистить",12,blue,true);
        clear.setGravity(Gravity.CENTER);
        clear.setBackground(shape(paleBlue,dp(13),0));
        padding(clear,dp(8),dp(12),dp(8),dp(12));
        LinearLayout.LayoutParams clearP=new LinearLayout.LayoutParams(0,-2,1);
        clearP.leftMargin=dp(5);
        buttons.addView(clear,clearP);
        TextView close=text("Закрыть",12,white,true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(shape(blue,dp(13),0));
        padding(close,dp(8),dp(12),dp(8),dp(12));
        LinearLayout.LayoutParams closeP=new LinearLayout.LayoutParams(0,-2,1);
        closeP.leftMargin=dp(5);
        buttons.addView(close,closeP);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(box).create();
        // Match the standard main-menu modal dim amount.
        dialog.setOnShowListener(ignored->styleCustomDialog(dialog,0.58f));
        refresh.setOnClickListener(v->{
            String latest=DebugLog.read(this);
            view.setText(latest.isEmpty()?"Журнал пока пуст. Повторите подключение, затем откройте этот экран.":latest);
            scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
        });
        share.setOnClickListener(v->{
            Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT,"SEYRO Lite — журнал отладки")
                .putExtra(Intent.EXTRA_TEXT,DebugLog.read(this));
            startActivity(Intent.createChooser(intent,"Отправить журнал"));
        });
        clear.setOnClickListener(v->{
            DebugLog.clear(this);
            dialog.dismiss();
            Toast.makeText(this,"Журнал очищен",Toast.LENGTH_SHORT).show();
        });
        close.setOnClickListener(v->dialog.dismiss());
        dialog.show();
    }
    private void showSubscription() {
        StringBuilder info=new StringBuilder();
        if(profileLink.isEmpty()) info.append("Ссылка подписки ещё не добавлена. Откройте «Профиль и безопасность».");
        else {
            if(!subscriptionTitle.isEmpty()) info.append(subscriptionTitle).append("\n\n");
            if(trafficUpload>=0 || trafficDownload>=0) {
                info.append("Отправлено: ").append(trafficUpload<0?"—":formatBytes(trafficUpload)).append("\n");
                info.append("Получено: ").append(trafficDownload<0?"—":formatBytes(trafficDownload)).append("\n");
                if(trafficTotal>0) {
                    long used=Math.max(0,trafficUpload)+Math.max(0,trafficDownload);
                    info.append("Использовано: ").append(formatBytes(used)).append(" из ").append(formatBytes(trafficTotal)).append("\n");
                    info.append("Осталось: ").append(formatBytes(Math.max(0,trafficTotal-used))).append("\n");
                }
            } else info.append("Данные о трафике сервером не переданы.\n");
            if(subscriptionExpire>0) info.append("Действует до: ").append(new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(subscriptionExpire*1000L))).append("\n");
            if(!subscriptionAnnounce.isEmpty()) info.append("\n").append(subscriptionAnnounce);
        }
        new AlertDialog.Builder(this).setTitle("Моя подписка")
            .setMessage(info.toString())
            .setPositiveButton(profileLink.isEmpty()?"Добавить ссылку":"ОК",(d,w)->{if(profileLink.isEmpty())showProfile();}).show();
    }

    private void updateSubscriptionWidgets() {
        if(subscriptionExpiryText!=null) {
            if(subscriptionInactive) {
                subscriptionExpiryText.setText("Подписка неактивна · доступ запрещён");
                subscriptionExpiryText.setTextColor(0xffc43d4b);
            } else if(profileLink.isEmpty()) {
                subscriptionExpiryText.setText("Добавьте ссылку, чтобы проверить подписку");
                subscriptionExpiryText.setTextColor(muted);
            } else if(subscriptionExpire>0 && isSubscriptionExpired()) {
                subscriptionExpiryText.setText("Подписка истекла "+formattedSubscriptionExpiry());
                subscriptionExpiryText.setTextColor(0xffc43d4b);
            } else if(subscriptionExpire>0) {
                subscriptionExpiryText.setText("Подписка активна до "+formattedSubscriptionExpiry());
                subscriptionExpiryText.setTextColor(green);
            } else if(subscriptionExpire==0) {
                subscriptionExpiryText.setText("VIP-доступ · срок не ограничен");
                subscriptionExpiryText.setTextColor(green);
            } else {
                subscriptionExpiryText.setText("Срок подписки не удалось проверить");
                subscriptionExpiryText.setTextColor(muted);
            }
        }
        if(trafficSummary==null || trafficPercent==null || trafficProgressFill==null || trafficBarTrack==null) return;
        if(profileLink.isEmpty()) {
            trafficPercent.setText("—");
            trafficSummary.setText("Добавьте ссылку подписки");
            trafficProgressFill.setVisibility(View.GONE);
            return;
        }
        if(subscriptionInactive) {
            trafficPercent.setText("—");
            trafficSummary.setText("Подписка неактивна · проверьте статус в личном кабинете");
            trafficProgressFill.setVisibility(View.GONE);
            return;
        }
        boolean unlimitedVip=isUnlimitedVip();
        if(unlimitedVip) {
            // VIP is unlimited even if the subscription endpoint still sends a finite
            // total (for example a default 10 GB quota). Never calculate a percentage
            // or remaining traffic for an unlimited VIP account.
            trafficPercent.setText("∞");
            trafficProgressFill.setVisibility(View.GONE);
            if(trafficUpload<0 && trafficDownload<0) {
                trafficSummary.setText("Безлимитный трафик · данные об использованном объёме пока недоступны");
            } else {
                long used=Math.max(0,trafficUpload)+Math.max(0,trafficDownload);
                trafficSummary.setText(formatBytes(used)+" использовано · безлимитный трафик");
            }
            return;
        }
        if(trafficUpload<0 && trafficDownload<0) {
            trafficPercent.setText("—");
            trafficSummary.setText(isSubscriptionExpired()
                ? "Подписка истекла · данные о трафике недоступны"
                : "Данные о трафике пока недоступны от сервера");
            trafficProgressFill.setVisibility(View.GONE);
            return;
        }
        long used=Math.max(0,trafficUpload)+Math.max(0,trafficDownload);
        if(trafficTotal<=0) {
            trafficPercent.setText("—");
            trafficSummary.setText(isSubscriptionExpired()
                ? "Подписка истекла · данные о лимите недоступны"
                : "Лимит трафика пока недоступен от сервера");
            trafficProgressFill.setVisibility(View.GONE);
            return;
        }
        int percent=(int)Math.min(100,Math.round(used*100.0/trafficTotal));
        trafficPercent.setText(percent+"%");
        trafficSummary.setText(formatBytes(used)+" из "+formatBytes(trafficTotal)+" · осталось "+formatBytes(Math.max(0,trafficTotal-used)));
        trafficProgressFill.setVisibility(View.VISIBLE);
        trafficProgressFill.setBackground(shape(percent>=90?0xffd94b55:blue,dp(20),0));
        trafficBarTrack.post(()->{
            int width=trafficBarTrack.getWidth();
            if(width>0) {
                LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)trafficProgressFill.getLayoutParams();
                p.width=(int)(width*percent/100.0);
                p.height=dp(7);
                trafficProgressFill.setLayoutParams(p);
            }
        });
    }

    private void clearCachedSubscriptionMetadata() {
        subscriptionTitle="";
        subscriptionAnnounce="";
        subscriptionInactive=false;
        trafficUpload=-1;
        trafficDownload=-1;
        trafficTotal=-1;
        subscriptionExpire=-1;
        prefs.edit()
            .remove("cached_subscription_title")
            .remove("cached_subscription_announce")
            .remove("cached_traffic_upload")
            .remove("cached_traffic_download")
            .remove("cached_traffic_total")
            .remove("cached_subscription_expire")
            .remove("cached_subscription_updated_at")
            .apply();
    }

    private void loadCachedSubscriptionMetadata() {
        subscriptionTitle=prefs.getString("cached_subscription_title","");
        subscriptionInactive=prefs.getBoolean("cached_subscription_inactive",false);
        subscriptionAnnounce=prefs.getString("cached_subscription_announce","");
        trafficUpload=prefs.getLong("cached_traffic_upload",-1);
        trafficDownload=prefs.getLong("cached_traffic_download",-1);
        trafficTotal=prefs.getLong("cached_traffic_total",-1);
        subscriptionExpire=prefs.getLong("cached_subscription_expire",-1);
    }

    private volatile boolean subscriptionRefreshInProgress=false;
    private long lastSubscriptionRefreshAt=0L;
    private boolean startupRefreshToastPending=true;
    private static final String PREF_SUBSCRIPTION_RETRY_AT="subscription_retry_at";
    private static final String PREF_SUBSCRIPTION_RETRY_ATTEMPT="subscription_retry_attempt";
    private static final long[] SUBSCRIPTION_BACKOFF_MS={60_000L,120_000L,300_000L,900_000L};

    private void refreshSubscriptionMetadataInBackground() {
        refreshSubscriptionMetadataInBackground(false);
    }

    private synchronized void refreshSubscriptionMetadataInBackground(boolean force) {
        final String link=profileLink;
        long now=System.currentTimeMillis();
        if(link==null || link.trim().isEmpty()) return;
        long retryAt=prefs.getLong(PREF_SUBSCRIPTION_RETRY_AT,0L);
        // Backoff applies only to automatic lifecycle refreshes. An explicit user
        // request from Settings must run immediately, regardless of the automatic retry timer.
        if(!force && now<retryAt) {
            long waitSeconds=Math.max(1L,(retryAt-now+999L)/1000L);
            DebugLog.d(this,"SUBSCRIPTION","Пропуск запроса (backoff): повтор через "+waitSeconds+" с");
            return;
        }
        // Lifecycle callbacks can fire back-to-back (create/settings). Keep a short
        // cooldown for ordinary requests and suppress concurrent requests.
        if(subscriptionRefreshInProgress || (!force && (now-lastSubscriptionRefreshAt)<30000L)) return;
        subscriptionRefreshInProgress=true;
        lastSubscriptionRefreshAt=now;
        new Thread(() -> {
            java.net.HttpURLConnection conn=null;
            try {
                URL url=new URL(link);
                if(!"https".equalsIgnoreCase(url.getProtocol()) && !"http".equalsIgnoreCase(url.getProtocol())) return;
                conn=(java.net.HttpURLConnection)url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent","SEYRO-Lite/"+APP_VERSION);
                DebugLog.d(this, "SUBSCRIPTION", "Ожидание HTTP-ответа подписки");
            int code=conn.getResponseCode();
                if(code>=200 && code<300) {
                    prefs.edit()
                        .putLong(PREF_SUBSCRIPTION_RETRY_AT,0L)
                        .putInt(PREF_SUBSCRIPTION_RETRY_ATTEMPT,0)
                        .apply();
                    subscriptionInactive=false;
                    prefs.edit().putBoolean("cached_subscription_inactive",false).apply();
                    readSubscriptionMetadata(conn);
                    runOnUiThread(() -> {
                        if(!link.equals(profileLink)) return;
                        if(userGreeting!=null) userGreeting.setText(greetingText());
                        updateSubscriptionWidgets();
                        updateProfileSubscriptionState();
                    });
                    DebugLog.i(this,"SUBSCRIPTION","Кэш подписки обновлён при запуске; HTTP "+code);
                    if(startupRefreshToastPending) {
                        startupRefreshToastPending=false;
                        runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,"Данные подписки обновлены",Toast.LENGTH_SHORT).show());
                    }
                } else if(code==403) {
                    String errorBody="";
                    try(InputStream error=conn.getErrorStream()) {
                        if(error!=null) {
                            ByteArrayOutputStream out=new ByteArrayOutputStream();
                            byte[] buf=new byte[1024]; int n;
                            while((n=error.read(buf))!=-1 && out.size()<8192) out.write(buf,0,n);
                            errorBody=new String(out.toByteArray(),StandardCharsets.UTF_8);
                        }
                    } catch(Exception ignored) {}
                    subscriptionInactive=true;
                    subscriptionTitle="";
                    subscriptionAnnounce="";
                    trafficUpload=-1; trafficDownload=-1; trafficTotal=-1; subscriptionExpire=-1;
                    prefs.edit()
                        .putBoolean("cached_subscription_inactive",true)
                        .remove("cached_subscription_title")
                        .remove("cached_subscription_announce")
                        .remove("cached_traffic_upload")
                        .remove("cached_traffic_download")
                        .remove("cached_traffic_total")
                        .remove("cached_subscription_expire")
                        .putLong(PREF_SUBSCRIPTION_RETRY_AT,0L)
                        .putInt(PREF_SUBSCRIPTION_RETRY_ATTEMPT,0)
                        .apply();
                    runOnUiThread(() -> {
                        if(!link.equals(profileLink)) return;
                        if(userGreeting!=null) userGreeting.setText("Подписка неактивна");
                        updateSubscriptionWidgets();
                        updateProfileSubscriptionState();
                    });
                    DebugLog.w(this,"SUBSCRIPTION","HTTP 403 — подписка неактивна; ответ сервера: "+
                        errorBody.replaceAll("[\\r\\n]+"," ").trim());
                } else if(code==429) {
                    int attempt=prefs.getInt(PREF_SUBSCRIPTION_RETRY_ATTEMPT,0);
                    int delayIndex=Math.min(attempt,SUBSCRIPTION_BACKOFF_MS.length-1);
                    long delay=SUBSCRIPTION_BACKOFF_MS[delayIndex];
                    long nextRetry=System.currentTimeMillis()+delay;
                    prefs.edit()
                        .putInt(PREF_SUBSCRIPTION_RETRY_ATTEMPT,Math.min(attempt+1,SUBSCRIPTION_BACKOFF_MS.length-1))
                        .putLong(PREF_SUBSCRIPTION_RETRY_AT,nextRetry)
                        .apply();
                    DebugLog.w(this,"SUBSCRIPTION","HTTP 429; backoff "+
                        (delay/60000L)+" мин, следующая попытка не раньше "+nextRetry);
                } else DebugLog.w(this,"SUBSCRIPTION","Обновление метаданных при запуске: HTTP "+code);
            } catch(Exception e) {
                DebugLog.w(this,"SUBSCRIPTION","Не удалось обновить метаданные при запуске: "+
                    e.getClass().getSimpleName()+": "+e.getMessage()+"\n"+android.util.Log.getStackTraceString(e));
            } finally {
                if(conn!=null) conn.disconnect();
                synchronized(MainActivity.this) { subscriptionRefreshInProgress=false; }
            }
        }).start();
    }

    private boolean isSubscriptionExpired() {
        return subscriptionExpire>0 && subscriptionExpire*1000L<=System.currentTimeMillis();
    }

    private String formattedSubscriptionExpiry() {
        return new java.text.SimpleDateFormat("dd.MM.yyyy 'в' HH:mm",java.util.Locale.getDefault())
            .format(new java.util.Date(subscriptionExpire*1000L));
    }

    private void updateProfileSubscriptionState() {
        if(subscriptionState==null) return;
        if(profileLink.isEmpty()) {
            subscriptionState.setText("Не настроена");
            return;
        }
        if(subscriptionInactive) {
            subscriptionState.setText("Неактивна / заблокирована");
            return;
        }
        if(subscriptionExpire>0) {
            String date=new java.text.SimpleDateFormat("dd.MM.yyyy",java.util.Locale.getDefault())
                .format(new java.util.Date(subscriptionExpire*1000L));
            subscriptionState.setText(isSubscriptionExpired()?"Истекла "+date:"Активна до "+date);
        } else {
            subscriptionState.setText("Профиль добавлен");
        }
    }

    private boolean isUnlimitedVip() {
        String title=subscriptionTitle==null?"":subscriptionTitle.toLowerCase(java.util.Locale.ROOT);
        String announce=subscriptionAnnounce==null?"":subscriptionAnnounce.toLowerCase(java.util.Locale.ROOT);
        // Confirmed server behavior: normal subscriptions can also send total=0.
        // VIP is identified by the explicit SEYRO VIP title or VIP announcement.
        return title.contains("vip")
            || announce.contains("vip-доступ")
            || announce.contains("vip access")
            || announce.contains("vip");
    }

    private String greetingText() {
        String greeting=!subscriptionAnnounce.isEmpty()?subscriptionAnnounce:
            (!subscriptionTitle.isEmpty()?"Добро пожаловать, "+subscriptionTitle:"Ваш интернет.\nПод защитой.");
        String announce=subscriptionAnnounce==null?"":subscriptionAnnounce.toLowerCase(java.util.Locale.ROOT);
        if(isUnlimitedVip()
                && !announce.contains("vip-доступ")
                && !announce.contains("vip access")
                && !announce.contains("vip"))
            return greeting+" · VIP-доступ активен";
        return greeting;
    }

    private String decodeMetadataHeader(String value) {
        if(value==null) return "";
        String v=value.trim();
        if(v.regionMatches(true,0,"base64:",0,7)) {
            try { return new String(Base64.decode(v.substring(7),Base64.DEFAULT),StandardCharsets.UTF_8).trim(); }
            catch(Exception ignored) {}
        }
        return v;
    }

    private String formatBytes(long bytes) {
        if(bytes<1024) return bytes+" Б";
        double value=bytes; String[] units={"Б","КБ","МБ","ГБ","ТБ"}; int i=0;
        while(value>=1024 && i<units.length-1) { value/=1024.0; i++; }
        return String.format(java.util.Locale.getDefault(),"%.2f %s",value,units[i]);
    }

    private void readSubscriptionMetadata(java.net.HttpURLConnection conn) {
        String title=decodeMetadataHeader(conn.getHeaderField("profile-title"));
        String announce=decodeMetadataHeader(conn.getHeaderField("announce"));
        subscriptionTitle=title;
        // announce is authoritative too: clear a previous VIP message when the server
        // stops sending it (for example after VIP is disabled).
        subscriptionAnnounce=announce;
        String userInfo=conn.getHeaderField("subscription-userinfo");
        // Every successful response replaces the previous metadata. Reset counters first
        // so a missing total field cannot leave an old 10 GB quota in the UI.
        trafficUpload=-1;
        trafficDownload=-1;
        trafficTotal=-1;
        subscriptionExpire=-1;
        if(userInfo!=null && !userInfo.trim().isEmpty()) {
            boolean hasExpire=false;
            for(String part:userInfo.split(";")) {
                String[] kv=part.trim().split("=",2);
                if(kv.length!=2) continue;
                String key=kv[0].trim().toLowerCase(java.util.Locale.ROOT);
                try {
                    long n=Long.parseLong(kv[1].trim());
                    switch(key) {
                        case "upload": trafficUpload=n; break;
                        case "download": trafficDownload=n; break;
                        case "total": trafficTotal=n; break;
                        case "expire": subscriptionExpire=Math.max(0,n); hasExpire=true; break;
                    }
                } catch(NumberFormatException ignored) {}
            }
            // A present userinfo header without expire means no expiry limit.
            if(!hasExpire) subscriptionExpire=0;
        } else if(announce.toLowerCase(java.util.Locale.ROOT).contains("vip-доступ активен")
                || announce.toLowerCase(java.util.Locale.ROOT).contains("vip access active")) {
            // This server represents unlimited VIP by an announce header without userinfo.
            subscriptionExpire=0;
        }
        prefs.edit()
            .putString("cached_subscription_title",subscriptionTitle)
            .putString("cached_subscription_announce",subscriptionAnnounce)
            .putLong("cached_traffic_upload",trafficUpload)
            .putLong("cached_traffic_download",trafficDownload)
            .putLong("cached_traffic_total",trafficTotal)
            .putLong("cached_subscription_expire",subscriptionExpire)
            .putLong("cached_subscription_updated_at",System.currentTimeMillis())
            .apply();
        DebugLog.i(this,"SUBSCRIPTION","Метаданные сохранены в кэш: title="+(!subscriptionTitle.isEmpty())+
            "; announce="+(!subscriptionAnnounce.isEmpty())+"; userinfo="+(userInfo!=null));
    }

    private static final String UPDATE_API = "https://api.github.com/repos/pavelzolotovng-oss/seyro-lite/releases?per_page=1";
    private volatile boolean updateCheckInProgress = false;
    private boolean pendingInstallAfterPermission = false;

    private synchronized void checkForAppUpdate(boolean force) {
        if (updateCheckInProgress) {
            if (force) showSimpleStyledNotice("Проверка обновлений",
                "Проверка уже выполняется. Подождите немного.");
            return;
        }
        long last = prefs.getLong("last_update_check_at", 0L);
        if (!force && System.currentTimeMillis() - last < 6L * 60L * 60L * 1000L) return;
        updateCheckInProgress = true;
        if (force) Toast.makeText(this, "Проверяем обновления SEYRO Lite…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            java.net.HttpURLConnection conn = null;
            try {
                conn = (java.net.HttpURLConnection) new URL(UPDATE_API).openConnection();
                conn.setConnectTimeout(8000); conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                conn.setRequestProperty("User-Agent", "SEYRO-Lite/" + APP_VERSION);
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) throw new java.io.IOException("GitHub HTTP " + code);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] buf = new byte[4096]; int n;
                    while ((n = in.read(buf)) != -1 && out.size() < 1000000) out.write(buf, 0, n);
                }
                JSONArray releases = new JSONArray(new String(out.toByteArray(), StandardCharsets.UTF_8));
                if (releases.length() == 0) {
                    if (force) runOnUiThread(() -> showSimpleStyledNotice("Обновления",
                        "Пока нет опубликованных сборок. Попробуйте проверить позже."));
                    return;
                }
                JSONObject release = releases.getJSONObject(0);
                if (release.optBoolean("draft", true)) {
                    if (force) runOnUiThread(() -> showSimpleStyledNotice("Обновления",
                        "Не удалось найти опубликованную сборку. Попробуйте позже."));
                    return;
                }
                int remoteCode = parseBuildCode(release.optString("tag_name", ""));
                if (remoteCode <= BuildConfig.VERSION_CODE) {
                    prefs.edit().putLong("last_update_check_at", System.currentTimeMillis()).apply();
                    if (force) runOnUiThread(() -> showSimpleStyledNotice("Обновлений нет",
                        "У вас установлена последняя версия SEYRO Lite (" + APP_VERSION + ")."));
                    return;
                }
                JSONArray assets = release.optJSONArray("assets");
                String apkUrl = null;
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset != null && "app-debug.apk".equals(asset.optString("name"))) {
                        apkUrl = asset.optString("browser_download_url", null); break;
                    }
                }
                if (apkUrl == null || !apkUrl.startsWith("https://github.com/")) {
                    if (force) runOnUiThread(() -> showSimpleStyledNotice("Обновление недоступно",
                        "Новая версия найдена, но APK пока не прикреплён к релизу."));
                    return;
                }
                prefs.edit().putLong("last_update_check_at", System.currentTimeMillis()).apply();
                final String version = release.optString("name", "сборка " + remoteCode);
                final String notes = release.optString("body", "");
                final String download = apkUrl;
                runOnUiThread(() -> showAppUpdateDialog(version, notes, download));
            } catch (Exception e) {
                DebugLog.w(this, "UPDATE", "Проверка обновлений не удалась: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                if (force) runOnUiThread(() -> showSimpleStyledNotice("Не удалось проверить обновления",
                    "Проверьте интернет-соединение и попробуйте ещё раз."));
            } finally {
                if (conn != null) conn.disconnect();
                synchronized (MainActivity.this) { updateCheckInProgress = false; }
            }
        }, "seyro-update-check").start();
    }

    private int parseBuildCode(String tag) {
        if (tag == null || !tag.startsWith("debug-build-")) return -1;
        try { return Integer.parseInt(tag.substring("debug-build-".length())); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private void showAppUpdateDialog(String version, String notes, String apkUrl) {
        if (isFinishing()) return;
        LinearLayout box=column();
        padding(box,dp(22),dp(20),dp(22),dp(14));
        box.setBackground(shape(white,dp(24),line));

        TextView brand=text("SEYRO",22,ink,true);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(.08f);
        box.addView(brand);
        TextView subtitle=text("LITE VPN  ·  SEYRO.RU",10,blue,true);
        subtitle.setGravity(Gravity.CENTER);
        add(box,subtitle,dp(3));

        TextView heading=text("Доступно обновление",19,ink,true);
        heading.setGravity(Gravity.CENTER);
        add(box,heading,dp(18));
        TextView versionText=text("Новая версия: "+version+"\nУстановлена: "+APP_VERSION,14,muted,false);
        versionText.setGravity(Gravity.CENTER);
        add(box,versionText,dp(9));
        TextView details=text("Обновление установится поверх приложения и сохранит ваши настройки.",13,muted,false);
        details.setGravity(Gravity.CENTER);
        add(box,details,dp(12));
        TextView changesHeading=text("Что нового",13,ink,true);
        changesHeading.setGravity(Gravity.START);
        add(box,changesHeading,dp(14));
        String visibleNotes=notes==null?"":notes.trim();
        if(visibleNotes.isEmpty()) visibleNotes="Список изменений для этой сборки пока не опубликован.";
        TextView releaseNotes=text(visibleNotes,12,muted,false);
        releaseNotes.setGravity(Gravity.START);
        releaseNotes.setLineSpacing(dp(2),1.0f);
        padding(releaseNotes,dp(12),dp(10),dp(12),dp(10));
        releaseNotes.setBackground(shape(0xfff3f7fd,dp(14),0xffe1eaf7));
        ScrollView notesScroll=new ScrollView(this);
        notesScroll.setFillViewport(false);
        notesScroll.setVerticalScrollBarEnabled(true);
        notesScroll.addView(releaseNotes,new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams notesP=new LinearLayout.LayoutParams(-1,dp(132));
        notesP.topMargin=dp(7);
        box.addView(notesScroll,notesP);
        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsP=new LinearLayout.LayoutParams(-1,-2);
        actionsP.topMargin=dp(18);
        box.addView(actions,actionsP);
        TextView later=text("Позже",13,muted,true);
        later.setGravity(Gravity.CENTER);
        later.setBackground(shape(0xfff2f6fc,dp(14),0));
        padding(later,dp(10),dp(12),dp(10),dp(12));
        actions.addView(later,new LinearLayout.LayoutParams(0,-2,1));
        TextView download=text("Скачать и установить",13,white,true);
        download.setGravity(Gravity.CENTER);
        download.setBackground(shape(blue,dp(14),0));
        padding(download,dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams downloadP=new LinearLayout.LayoutParams(0,-2,1);
        downloadP.leftMargin=dp(8);
        actions.addView(download,downloadP);

        AlertDialog dialog=new AlertDialog.Builder(this).setView(box).create();
        dialog.setOnShowListener(v->styleCustomDialog(dialog,0.92f));
        dialog.show();
        later.setOnClickListener(v->dialog.dismiss());
        download.setOnClickListener(v->{dialog.dismiss();downloadUpdate(apkUrl);});
    }

    private void downloadUpdate(String apkUrl) {
        if(isFinishing()) return;
        LinearLayout box=column();
        padding(box,dp(22),dp(22),dp(22),dp(20));
        box.setBackground(shape(white,dp(24),line));
        TextView brand=text("SEYRO",20,ink,true);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(.08f);
        box.addView(brand);
        TextView title=text("Загрузка обновления",16,ink,true);
        title.setGravity(Gravity.CENTER);
        add(box,title,dp(12));
        TextView percentText=text("Подготовка загрузки…",13,muted,false);
        percentText.setGravity(Gravity.CENTER);
        add(box,percentText,dp(10));
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(blue));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffe2eaf6));
        LinearLayout.LayoutParams progressP=new LinearLayout.LayoutParams(-1,dp(8));
        progressP.topMargin=dp(14);
        box.addView(progress,progressP);
        TextView statusText=text("Не закрывайте приложение до завершения.",11,muted,false);
        statusText.setGravity(Gravity.CENTER);
        add(box,statusText,dp(10));
        AlertDialog progressDialog=new AlertDialog.Builder(this).setView(box).create();
        progressDialog.setCancelable(false);
        progressDialog.setOnShowListener(v->styleCustomDialog(progressDialog,0.86f));
        progressDialog.show();

        new Thread(() -> {
            java.net.HttpURLConnection conn = null;
            try {
                java.io.File dir = new java.io.File(getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("Не удалось создать каталог обновления");
                java.io.File apk = new java.io.File(dir, "seyro-lite-update.apk");
                conn = (java.net.HttpURLConnection) new URL(apkUrl).openConnection();
                conn.setConnectTimeout(15000); conn.setReadTimeout(30000); conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "SEYRO-Lite/" + APP_VERSION);
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) throw new java.io.IOException("HTTP " + code);
                long expected=conn.getContentLengthLong();
                if(expected>300L*1024L*1024L) throw new java.io.IOException("APK превышает допустимый размер (300 МБ)");
                runOnUiThread(() -> {
                    progress.setIndeterminate(expected<=0);
                    percentText.setText(expected>0?"Загрузка: 0%":"Загрузка…");
                });
                try (InputStream in = conn.getInputStream(); java.io.FileOutputStream fileOut = new java.io.FileOutputStream(apk)) {
                    byte[] buf = new byte[8192]; int n; long total = 0; int lastPercent=-1;
                    while ((n = in.read(buf)) != -1) {
                        total += n;
                        if (total > 300L * 1024L * 1024L) throw new java.io.IOException("APK превышает допустимый размер (300 МБ)");
                        fileOut.write(buf, 0, n);
                        if(expected>0) {
                            int p=(int)Math.min(100,total*100L/expected);
                            if(p!=lastPercent) {
                                lastPercent=p;
                                final int shown=p;
                                final long downloaded=total;
                                runOnUiThread(() -> {
                                    progress.setIndeterminate(false);
                                    progress.setProgress(shown);
                                    percentText.setText("Загрузка: "+shown+"%");
                                    statusText.setText(formatBytes(downloaded)+" из "+formatBytes(expected));
                                });
                            }
                        }
                    }
                    fileOut.getFD().sync();
                }
                if (!apk.isFile() || apk.length() == 0) throw new java.io.IOException("Скачанный APK пуст");
                runOnUiThread(() -> {
                    progress.setProgress(100);
                    percentText.setText("Загрузка завершена · 100%");
                    progressDialog.dismiss();
                    installDownloadedUpdate(apk);
                });
            } catch (Exception e) {
                DebugLog.w(this, "UPDATE", "Не удалось скачать обновление: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                runOnUiThread(() -> {
                    if(progressDialog.isShowing()) progressDialog.dismiss();
                    new AlertDialog.Builder(this).setTitle("Не удалось обновить SEYRO")
                        .setMessage("Не удалось скачать установочный файл. Проверьте соединение и попробуйте ещё раз.")
                        .setPositiveButton("Повторить", (d, w) -> downloadUpdate(apkUrl))
                        .setNegativeButton("Закрыть", null).show();
                });
            } finally { if (conn != null) conn.disconnect(); }
        }, "seyro-update-download").start();
    }

    private void installDownloadedUpdate(java.io.File apk) {
        if (apk == null || !apk.isFile() || apk.length() == 0) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            pendingInstallAfterPermission = true;
            new AlertDialog.Builder(this).setTitle("Разрешите установку обновлений")
                .setMessage("Android требует разрешить установку приложений из этого источника. Включите разрешение для SEYRO, затем вернитесь в приложение.")
                .setPositiveButton("Открыть настройки", (d, w) -> startActivity(new Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Позже", (d, w) -> pendingInstallAfterPermission = false).show();
            return;
        }
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (Exception e) {
            DebugLog.e(this, "UPDATE", "Не удалось открыть установщик: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            Toast.makeText(this, "Не удалось открыть установщик Android", Toast.LENGTH_LONG).show();
        }
    }

    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("Помощь")
            .setMessage("Добавьте постоянную ссылку из личного кабинета SEYRO. Добавьте ссылку профиля VLESS + REALITY из личного кабинета SEYRO. Если подключение не запускается, проверьте ссылку и доступ к серверу.")
            .setPositiveButton("Понятно",null).show();
    }
    private void onConnect() {
        DebugLog.i(this,"UI","Нажата кнопка подключения; state="+SeyroVpnService.vpnState);
        if("connected".equals(SeyroVpnService.vpnState)) {
            connect.setEnabled(false); connect.setText("Отключаемся…");
            Intent stop=new Intent(this,SeyroVpnService.class); stop.setAction(SeyroVpnService.ACTION_STOP); startService(stop);
            return;
        }
        if("connecting".equals(SeyroVpnService.vpnState)||"disconnecting".equals(SeyroVpnService.vpnState)) return;
        if(profileLink.isEmpty()) { showProfile(); return; }
        if(!selectedConfig.isEmpty()) { requestVpnPermissionAndStart(); return; }
        // The transport preference survives app restarts; restore its matching config
        // from the subscription before asking the user to choose again.
        boolean manualMode="manual".equals(prefs.getString("selection_mode","auto"));
        String savedTransport=prefs.getString("selected_transport","");
        if(manualMode && !savedTransport.isEmpty()) {
            connect.setEnabled(false); connect.setText("Восстанавливаем профиль…");
            new Thread(() -> {
                java.util.ArrayList<ProfileOption> configs=null;
                String problem=null;
                try { configs=fetchAndBuildConfigs(profileLink); }
                catch(Exception e) { problem=e.getMessage()==null?"Не удалось загрузить профили":e.getMessage(); }
                final java.util.ArrayList<ProfileOption> loaded=configs;
                final String error=problem;
                runOnUiThread(() -> {
                    connect.setEnabled(true); connect.setText("Подключить");
                    if(error==null && loaded!=null) {
                        availableProfiles=loaded;
                        for(ProfileOption option:loaded) {
                            if(savedTransport.equalsIgnoreCase(option.transport)) {
                                selectProfile(option);
                                return;
                            }
                        }
                    }
                    new AlertDialog.Builder(this)
                        .setTitle("Выберите профиль в настройках")
                        .setMessage(error==null
                            ?"Сохранённый транспорт не найден в подписке. Выберите TCP или XHTTP через ⚙ → «Выбрать профиль подключения»."
                            :"Не удалось восстановить профиль: "+error)
                        .setPositiveButton("Открыть настройки",(d,w)->showSettings())
                        .setNegativeButton("Отмена",null)
                        .show();
                });
            }).start();
            return;
        }
        if(manualMode) {
            showStyledManualPrompt();
            return;
        }
        connect.setEnabled(false); connect.setText("Загрузка профиля…");
        new Thread(() -> {
            String error=null;
            java.util.ArrayList<ProfileOption> configs=null;
            try { configs=fetchAndBuildConfigs(profileLink); }
            catch(Exception e) { error=e.getMessage()==null?"Не удалось прочитать подписку":e.getMessage(); DebugLog.e(this,"SUBSCRIPTION","Ошибка загрузки/разбора: "+e.getClass().getSimpleName()+": "+error); }
            final java.util.ArrayList<ProfileOption> result=configs;
            final String problem=error;
            runOnUiThread(() -> {
                connect.setEnabled(true); connect.setText("Подключить");
                if(problem!=null) {
                    new AlertDialog.Builder(this).setTitle("Не удалось загрузить подписку")
                        .setMessage(problem).setPositiveButton("Изменить ссылку",(d,w)->showProfile())
                        .setNegativeButton("Закрыть",null).show();
                    return;
                }
                availableProfiles=result;
                if(result.size()>1 && "manual".equals(prefs.getString("selection_mode","auto"))) {
                    String[] labels=new String[result.size()];
                    for(int i=0;i<result.size();i++) labels[i]=result.get(i).label+" — "+result.get(i).transport.toUpperCase(java.util.Locale.ROOT);
                    new AlertDialog.Builder(this).setTitle("Выберите профиль подключения")
                        .setMessage("Выберите транспорт TCP или XHTTP.")
                        .setItems(labels,(d,which)->selectProfile(result.get(which)))
                        .setNegativeButton("Отмена",null).show();
                } else {
                    java.util.List<ProfileOption> ordered=buildAutoAttemptOrder(result);
                    autoFallbackProfiles.clear();
                    autoFallbackProfiles.addAll(ordered);
                    autoFallbackIndex=0;
                    StringBuilder queueLog=new StringBuilder();
                    for(ProfileOption p:ordered) {
                        if(queueLog.length()>0) queueLog.append(", ");
                        queueLog.append(p.transport);
                    }
                    DebugLog.i(this,"PROFILE","Автовыбор: очередь = ["+queueLog+"]");
                    ProfileOption chosen=ordered.get(0);
                    DebugLog.i(this,"PROFILE","Автовыбор: попытка 1/"+Math.min(AUTO_MAX_ATTEMPTS,ordered.size())
                        +" — "+chosen.transport+" ("+chosen.label+")");
                    selectProfile(chosen);
                }
            });
        }).start();
    }

    private java.util.List<ProfileOption> buildAutoAttemptOrder(
            java.util.ArrayList<ProfileOption> profiles) {
        java.util.LinkedHashSet<String> order = new java.util.LinkedHashSet<>();
        String saved = prefs.getString("auto_last_success_transport", "");
        if (!saved.isEmpty()) order.add(saved.toLowerCase(java.util.Locale.ROOT));
        for (String transport : AUTO_TRANSPORT_PRIORITY) order.add(transport);
        for (ProfileOption option : profiles) {
            if (option.transport != null && !option.transport.isEmpty())
                order.add(option.transport.toLowerCase(java.util.Locale.ROOT));
        }
        java.util.List<ProfileOption> result = new java.util.ArrayList<>();
        for (String transport : order) {
            for (ProfileOption option : profiles) {
                if (option.transport.equalsIgnoreCase(transport)) {
                    result.add(option);
                    break;
                }
            }
        }
        for (ProfileOption option : profiles) if (!result.contains(option)) result.add(option);
        return result;
    }

    private void handleAutoSelectionState() {
        if(!"auto".equals(prefs.getString("selection_mode","auto"))) return;
        if("connected".equals(SeyroVpnService.vpnState)) {
            // Connected confirms Xray/TUN startup only; transport is saved after a successful probe.
            autoFallbackProfiles.clear();
            autoFallbackIndex=0;
            handlingAutoFallback=false;
            return;
        }
        if(!"error".equals(SeyroVpnService.vpnState) || handlingAutoFallback
                || autoFallbackIndex+1>=autoFallbackProfiles.size()
                || autoFallbackIndex+1>=AUTO_MAX_ATTEMPTS) return;
        handlingAutoFallback=true;
        autoFallbackIndex++;
        ProfileOption next=autoFallbackProfiles.get(autoFallbackIndex);
        DebugLog.i(this,"PROFILE","Автовыбор: попытка "+(autoFallbackIndex+1)+"/"
            +Math.min(AUTO_MAX_ATTEMPTS,autoFallbackProfiles.size())+" — "+next.transport);
        selectedConfig=next.config;
        activeTransport=next.transport;
        prefs.edit().putString("selected_transport",activeTransport).apply();
        DebugLog.w(this,"AUTO","Не удалось запустить предыдущий профиль; пробуем "
            +(autoFallbackIndex+1)+"/"+autoFallbackProfiles.size()+": "+next.label+"; transport="+next.transport);
        if(connect!=null) { connect.setEnabled(false); connect.setText("Пробуем другой профиль…"); }
        if(hint!=null) hint.setText("Первый профиль не запустился. Пробуем "+next.transport.toUpperCase(java.util.Locale.ROOT)+"…");
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            handlingAutoFallback=false;
            if("auto".equals(prefs.getString("selection_mode","auto"))
                    && "error".equals(SeyroVpnService.vpnState)) {
                updateProtocolBadge();
                requestVpnPermissionAndStart();
            }
        },350);
    }

    private void selectProfile(ProfileOption option) {
        selectedConfig=option.config;
        activeTransport=option.transport;
        prefs.edit().putString("selected_transport",activeTransport).apply();
        DebugLog.i(this,"PROFILE","Выбран профиль: "+option.label+"; transport="+option.transport);
        updateProtocolBadge();
        hint.setText("Профиль: "+option.label+" ("+option.transport.toUpperCase(java.util.Locale.ROOT)+")");
        requestVpnPermissionAndStart();
    }

    private String detectTransport(String config) {
        try {
            JSONObject root=new JSONObject(config);
            JSONArray outbounds=root.optJSONArray("outbounds");
            if(outbounds!=null) for(int i=0;i<outbounds.length();i++) {
                JSONObject o=outbounds.optJSONObject(i);
                if(o!=null && "vless".equalsIgnoreCase(o.optString("protocol"))) {
                    JSONObject stream=o.optJSONObject("streamSettings");
                    return stream==null?"tcp":stream.optString("network","tcp");
                }
            }
        } catch(Exception ignored) {}
        return "unknown";
    }

    private java.util.ArrayList<ProfileOption> fetchAndBuildConfigs(String link) throws Exception {
        DebugLog.d(this, "SUBSCRIPTION", "Этап 1/5: подготовка URL подписки; секретная часть не записывается");
        DebugLog.i(this,"SUBSCRIPTION","Начата загрузка профиля; ссылка скрыта");
        URL url=new URL(link);
        if(!"https".equalsIgnoreCase(url.getProtocol()) && !"http".equalsIgnoreCase(url.getProtocol()))
            throw new IllegalArgumentException("Для подписки нужна ссылка HTTPS или HTTP.");
        java.net.HttpURLConnection conn=(java.net.HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(12000); conn.setReadTimeout(15000); conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent","SEYRO-Lite/"+APP_VERSION);
        try {
            int code=conn.getResponseCode();
            readSubscriptionMetadata(conn);
            runOnUiThread(() -> {
                if(userGreeting!=null) userGreeting.setText(greetingText());
                updateSubscriptionWidgets();
            });
            DebugLog.i(this,"SUBSCRIPTION","HTTP статус="+code+"; тип ответа="+conn.getContentType());
            if(code<200||code>=300) throw new IllegalStateException("Сервер подписки ответил HTTP "+code);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=conn.getInputStream()) {
                byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1) { out.write(buf,0,n); if(out.size()>2_000_000) throw new IllegalStateException("Ответ подписки слишком большой"); }
            }
            String body=new String(out.toByteArray(),StandardCharsets.UTF_8).trim();
            DebugLog.d(this, "SUBSCRIPTION", "Этап 2/5: тело ответа прочитано; размер=" + out.size() + " байт");
            DebugLog.i(this,"SUBSCRIPTION","Ответ получен; байт="+out.size()+"; начало формата="+(body.startsWith("[")?"JSON-массив":body.startsWith("{")?"JSON-объект":body.startsWith("vless://")?"VLESS":"другой/Base64"));
            DebugLog.d(this, "SUBSCRIPTION", "Этап 3/5: попытка разбора JSON-профилей");
            // Some providers return a JSON array of complete Xray profiles instead of vless:// links.
            if(body.startsWith("[") || body.startsWith("{")) {
                try {
                    JSONArray profiles = body.startsWith("[") ? new JSONArray(body) : new JSONArray().put(new JSONObject(body));
                    java.util.ArrayList<ProfileOption> parsed=new java.util.ArrayList<>();
                    for(int i=0;i<profiles.length();i++) {
                        JSONObject p=profiles.optJSONObject(i); if(p==null) continue;
                        try {
                            String cfg=buildJsonProfileConfig(p);
                            String label=p.optString("remarks",p.optString("name","Профиль "+(i+1)));
                            String transport=detectTransport(cfg);
                            parsed.add(new ProfileOption(label,transport,cfg));
                            DebugLog.i(this,"PROFILE","Найден профиль #"+(i+1)+": "+label+"; transport="+transport);
                        } catch(Exception ex) { DebugLog.w(this,"PROFILE","Пропущен профиль #"+(i+1)+": "+ex.getMessage()); }
                    }
                    if(!parsed.isEmpty()) return parsed;
                } catch(org.json.JSONException ignored) { /* Continue with Base64/link parsing. */ }
            }
            // Subscription providers may return plain links, standard Base64, or URL-safe Base64.
            // Try decoding more than once, because some gateways wrap an already encoded subscription.
            DebugLog.d(this, "SUBSCRIPTION", "Этап 4/5: поиск VLESS-ссылок / декодирование Base64");
            for(int attempt=0;attempt<3 && !body.contains("vless://");attempt++) {
                String compact=body.replaceAll("\\s+","");
                String decoded=null;
                int[] flags={Base64.DEFAULT,Base64.URL_SAFE|Base64.NO_WRAP,Base64.NO_WRAP};
                for(int flag:flags) {
                    try {
                        String candidate=new String(Base64.decode(compact,flag),StandardCharsets.UTF_8).trim();
                        if(!candidate.isEmpty() && !candidate.equals(body)) { decoded=candidate; break; }
                    } catch(Exception ignored) {}
                }
                if(decoded==null) break;
                body=decoded;
            }
            // A Base64 subscription may decode to JSON rather than to vless:// links.
            if(body.startsWith("[") || body.startsWith("{")) {
                try {
                    JSONArray profiles = body.startsWith("[") ? new JSONArray(body) : new JSONArray().put(new JSONObject(body));
                    java.util.ArrayList<ProfileOption> parsed=new java.util.ArrayList<>();
                    for(int i=0;i<profiles.length();i++) {
                        JSONObject p=profiles.optJSONObject(i); if(p==null) continue;
                        try {
                            String cfg=buildJsonProfileConfig(p);
                            String label=p.optString("remarks",p.optString("name","Профиль "+(i+1)));
                            String transport=detectTransport(cfg);
                            parsed.add(new ProfileOption(label,transport,cfg));
                            DebugLog.i(this,"PROFILE","Найден профиль #"+(i+1)+": "+label+"; transport="+transport);
                        } catch(Exception ex) { DebugLog.w(this,"PROFILE","Пропущен профиль #"+(i+1)+": "+ex.getMessage()); }
                    }
                    if(!parsed.isEmpty()) return parsed;
                } catch(org.json.JSONException ignored) { /* Continue with link parsing. */ }
            }
            DebugLog.d(this, "SUBSCRIPTION", "Этап 5/5: разбор строк подписки");
            for(String line:body.split("\\r?\n")) {
                String candidate=line.trim();
                int linkStart=candidate.indexOf("vless://");
                if(linkStart>=0) {
                    int linkEnd=candidate.indexOf(' ',linkStart);
                    if(linkEnd>linkStart) candidate=candidate.substring(linkStart,linkEnd);
                    else candidate=candidate.substring(linkStart);
                    try {
                        String cfg=buildVlessConfig(candidate);
                        java.util.ArrayList<ProfileOption> one=new java.util.ArrayList<>();
                        String label="VLESS-профиль";
                        int hash=candidate.lastIndexOf('#'); if(hash>=0 && hash<candidate.length()-1) label=URLDecoder.decode(candidate.substring(hash+1),"UTF-8");
                        one.add(new ProfileOption(label,detectTransport(cfg),cfg));
                        return one;
                    } catch(Exception ignored) { /* Try the next VLESS entry, if any. */ }
                }
            }
            throw new IllegalStateException("Ссылка открылась, но формат ответа подписки не распознан. Нужен список ссылок VLESS + REALITY; сервер мог вернуть другой формат или сообщение об ошибке.");
        } finally { conn.disconnect(); }
    }

    private String buildJsonProfileConfig(JSONObject profile) throws Exception {
        JSONArray sourceOutbounds=profile.optJSONArray("outbounds");
        if(sourceOutbounds==null) throw new IllegalArgumentException("В JSON-профиле нет outbounds.");
        JSONObject sourceProxy=null;
        for(int i=0;i<sourceOutbounds.length();i++) {
            JSONObject candidate=sourceOutbounds.optJSONObject(i);
            if(candidate!=null && "vless".equalsIgnoreCase(candidate.optString("protocol"))) {
                sourceProxy=candidate; break;
            }
        }
        if(sourceProxy==null) throw new IllegalArgumentException("В JSON-профиле нет VLESS outbound.");
        JSONObject sourceSettings=sourceProxy.optJSONObject("settings");
        JSONArray vnexts=sourceSettings==null?null:sourceSettings.optJSONArray("vnext");
        if(vnexts==null||vnexts.length()==0) throw new IllegalArgumentException("В JSON-профиле не указан сервер VLESS.");
        JSONObject sourceVnext=vnexts.getJSONObject(0);
        JSONArray sourceUsers=sourceVnext.optJSONArray("users");
        if(sourceUsers==null||sourceUsers.length()==0) throw new IllegalArgumentException("В JSON-профиле не указан пользователь VLESS.");
        JSONObject sourceUser=sourceUsers.getJSONObject(0);
        JSONObject sourceStream=sourceProxy.optJSONObject("streamSettings");
        if(sourceStream==null||!"reality".equalsIgnoreCase(sourceStream.optString("security")))
            throw new IllegalArgumentException("Профиль VLESS не использует REALITY.");
        JSONObject sourceReality=sourceStream.optJSONObject("realitySettings");
        if(sourceReality==null||sourceReality.optString("publicKey").isEmpty())
            throw new IllegalArgumentException("В профиле REALITY отсутствует публичный ключ.");

        // Preserve the provider's complete VLESS outbound. Rebuilding only a subset of
        // streamSettings silently drops transport-specific fields (sockopt, tcp/raw,
        // xhttp mode/host/path, mux, packetEncoding, and future Xray options).
        // Only normalize the tag because the app's routing rule targets "proxy".
        JSONObject outbound=new JSONObject(sourceProxy.toString()).put("tag","proxy");
        JSONObject direct=new JSONObject().put("tag","direct").put("protocol","freedom");
        JSONObject block=new JSONObject().put("tag","block").put("protocol","blackhole");
        JSONArray outbounds=new JSONArray().put(outbound).put(direct).put(block);
        JSONObject routing=new JSONObject().put("domainStrategy","AsIs")
            .put("rules",new JSONArray().put(new JSONObject().put("type","field").put("inboundTag",new JSONArray().put("socks-in")).put("outboundTag","proxy")));
        // HEV tun2socks feeds packets into this local SOCKS inbound; Xray itself does not own the TUN fd.
        JSONObject inbound=new JSONObject().put("tag","socks-in").put("listen","127.0.0.1").put("port",10808)
            .put("protocol","socks").put("settings",new JSONObject().put("auth","noauth").put("udp",true).put("ip","127.0.0.1"));
        return new JSONObject().put("log",new JSONObject().put("loglevel","debug"))
            .put("stats",new JSONObject())
            .put("policy",new JSONObject()
                .put("levels",new JSONObject().put("0",new JSONObject().put("statsUserUplink",true).put("statsUserDownlink",true)))
                .put("system",new JSONObject().put("statsOutboundUplink",true).put("statsOutboundDownlink",true)))
            .put("inbounds",new JSONArray().put(inbound)).put("outbounds",outbounds).put("routing",routing).toString();
    }

    private String decode(String value) throws Exception {
        return URLDecoder.decode(value, "UTF-8");
    }
    private String buildVlessConfig(String raw) throws Exception {
        URI uri=new URI(raw);
        String authority=uri.getRawAuthority();
        if(authority==null||!authority.contains("@")) throw new IllegalArgumentException("Некорректная VLESS-ссылка.");
        String[] auth=authority.split("@",2);
        String id=auth[0]; String hostPort=auth[1];
        int colon=hostPort.lastIndexOf(':');
        if(colon<1) throw new IllegalArgumentException("В профиле не указан порт сервера.");
        String host=hostPort.substring(0,colon); int port=Integer.parseInt(hostPort.substring(colon+1));
        java.util.Map<String,String> q=new java.util.HashMap<>();
        if(uri.getRawQuery()!=null) for(String pair:uri.getRawQuery().split("&")) {
            String[] kv=pair.split("=",2); q.put(decode(kv[0]),kv.length>1?decode(kv[1]):"");
        }
        String security=q.getOrDefault("security","none");
        String network=q.getOrDefault("type","tcp");
        if(!"reality".equalsIgnoreCase(security)) throw new IllegalArgumentException("Первый профиль не использует REALITY. Сейчас включена проверка VLESS + REALITY.");
        JSONObject user=new JSONObject().put("id",id).put("encryption","none");
        if(!q.getOrDefault("flow","").isEmpty()) user.put("flow",q.get("flow"));
        JSONArray users=new JSONArray().put(user);
        JSONObject vnext=new JSONObject().put("address",host).put("port",port).put("users",users);
        JSONObject settings=new JSONObject().put("vnext",new JSONArray().put(vnext));
        JSONObject reality=new JSONObject().put("serverName",q.getOrDefault("sni",host))
            .put("fingerprint",q.getOrDefault("fp","chrome"))
            .put("publicKey",q.getOrDefault("pbk",q.getOrDefault("publicKey","")))
            .put("shortId",q.getOrDefault("sid",""))
            .put("spiderX",q.getOrDefault("spx","/"));
        if(reality.optString("publicKey").isEmpty()) throw new IllegalArgumentException("В профиле REALITY отсутствует публичный ключ.");
        JSONObject stream=new JSONObject().put("network",network).put("security","reality").put("realitySettings",reality);
        JSONObject outbound=new JSONObject().put("tag","proxy").put("protocol","vless").put("settings",settings).put("streamSettings",stream);
        JSONObject direct=new JSONObject().put("tag","direct").put("protocol","freedom");
        JSONObject block=new JSONObject().put("tag","block").put("protocol","blackhole");
        JSONArray outbounds=new JSONArray().put(outbound).put(direct).put(block);
        JSONObject routing=new JSONObject().put("domainStrategy","AsIs")
            .put("rules",new JSONArray().put(new JSONObject().put("type","field").put("inboundTag",new JSONArray().put("socks-in")).put("outboundTag","proxy")));
        // HEV tun2socks feeds packets into this local SOCKS inbound; Xray itself does not own the TUN fd.
        JSONObject inbound=new JSONObject().put("tag","socks-in").put("listen","127.0.0.1").put("port",10808)
            .put("protocol","socks").put("settings",new JSONObject().put("auth","noauth").put("udp",true).put("ip","127.0.0.1"));
        // Xray's native TUN inbound consumes the VpnService file descriptor passed to StartLoop.
        DebugLog.d(this, "XRAY_CONFIG", "Сформирован Xray-конфиг для транспорта " + network + "; содержимое конфига не выводится");
        return new JSONObject().put("log",new JSONObject().put("loglevel","debug"))
            .put("stats",new JSONObject())
            .put("policy",new JSONObject()
                .put("levels",new JSONObject().put("0",new JSONObject().put("statsUserUplink",true).put("statsUserDownlink",true)))
                .put("system",new JSONObject().put("statsOutboundUplink",true).put("statsOutboundDownlink",true)))
            .put("inbounds",new JSONArray().put(inbound)).put("outbounds",outbounds).put("routing",routing).toString();
    }

    private void requestVpnPermissionAndStart() {
        DebugLog.d(this, "VPN", "Проверка разрешения Android VPN; выбранный транспорт=" + activeTransport);
        DebugLog.i(this,"VPN","Проверка разрешения Android VPN");
        Intent permission=VpnService.prepare(this);
        if(permission!=null) { DebugLog.i(this,"VPN","Запрошено разрешение Android VPN"); startActivityForResult(permission,VPN_PERMISSION_REQUEST); return; }
        startVpnService();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==VPN_PERMISSION_REQUEST) {
            if(resultCode==RESULT_OK) { DebugLog.i(this,"VPN","Разрешение Android VPN получено"); startVpnService(); }
            else { DebugLog.w(this,"VPN","Пользователь отклонил разрешение Android VPN"); new AlertDialog.Builder(this).setTitle("Нужно разрешение VPN")
                .setMessage("Android не разрешил создать VPN-туннель. Подключение не запущено.")
                .setPositiveButton("Понятно",null).show(); }
        }
    }
    private void startVpnService() {
        DebugLog.i(this,"VPN","Отправлена команда запуска службы; конфигурация не записывается");
        status.setText("●  ПОДКЛЮЧАЕМСЯ"); status.setTextColor(blue);
        connect.setText("Подождите…"); connect.setEnabled(false);
        hint.setText("Запускаем защищённый туннель…");
        Intent intent=new Intent(this,SeyroVpnService.class);
        intent.setAction(SeyroVpnService.ACTION_START);
        intent.putExtra(SeyroVpnService.EXTRA_CONFIG,selectedConfig);
        intent.putExtra(SeyroVpnService.EXTRA_TRANSPORT,activeTransport);
        boolean isAuto = "auto".equals(prefs.getString("selection_mode", "auto"));
        intent.putExtra(SeyroVpnService.EXTRA_BY_AUTO, isAuto);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(intent); else startService(intent);
    }
}
