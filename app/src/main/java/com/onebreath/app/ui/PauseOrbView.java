package com.onebreath.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * 喘口氣畫面的主角：一顆會呼吸的光球。刻意不顯示任何倒數（秒數、進度環），不讓人盯著時間等。
 *
 * <ul>
 *   <li>呼吸：4 秒吸氣（光球與外層光暈慢慢撐開）、4 秒吐氣（收回），餘弦緩動。</li>
 *   <li>球心文字「吸氣 / 吐氣」跟著節奏切換，切換時淡出淡入。</li>
 *   <li>系統「移除動畫」開啟時光球靜止。</li>
 * </ul>
 * onDraw 內不配置任何物件。
 */
public class PauseOrbView extends View {

    private static final long HALF_BREATH_MS = 4000L;
    private static final long CYCLE_MS = HALF_BREATH_MS * 2;
    private static final long CUE_FADE_MS = 550L;
    private static final int RING_COUNT = 3;

    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cuePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    private int accent = 0xFF3D7BFF;
    private int accentLight = 0xFF8FB0FF;
    private int textColor = 0xFFEEF2FA;

    // 幾何（onSizeChanged 計算）
    private float cx, cy, ringR, ringStroke, coreMin, coreMax, haloMax;
    private float cueBaseline;
    private boolean sized;

    // 時間狀態
    private long breathStart = SystemClock.uptimeMillis();
    private boolean animate = true;
    private boolean running;


    private String inhaleText = "吸氣";
    private String exhaleText = "吐氣";
    private String stillText = "慢慢呼吸";

    public PauseOrbView(Context c) {
        super(c);
        init();
    }

    public PauseOrbView(Context c, AttributeSet attrs) {
        super(c, attrs);
        init();
    }

    public PauseOrbView(Context c, AttributeSet attrs, int defStyle) {
        super(c, attrs, defStyle);
        init();
    }

    private void init() {
        haloPaint.setStyle(Paint.Style.FILL);
        corePaint.setStyle(Paint.Style.FILL);
        rimPaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStyle(Paint.Style.STROKE);
        cuePaint.setTextAlign(Paint.Align.CENTER);
        cuePaint.setLetterSpacing(0.35f);
        applyColors();
    }

    // ---------------------------------------------------------------- API

    /** 主色（光暈）與亮色（球心）。換模式時呼叫。 */
    public void setPalette(int accentColor, int accentLightColor, int textCol) {
        accent = accentColor;
        accentLight = accentLightColor;
        textColor = textCol;
        applyColors();
        if (sized) buildShaders();
        invalidate();
    }

    /** 開始呼吸。animations=false 時光球靜止（系統關閉動畫）。 */
    public void start(boolean animations) {
        animate = animations;
        breathStart = SystemClock.uptimeMillis();
        running = true;
        invalidate();
    }

    public void stop() {
        running = false;
    }

    public static boolean systemAnimationsEnabled() {
        return ValueAnimator.areAnimatorsEnabled();
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int max = dp(300);
        int byScreen = (int) (getResources().getDisplayMetrics().heightPixels * 0.40f);
        int want = Math.max(dp(160), Math.min(max, byScreen));
        int wMode = MeasureSpec.getMode(widthMeasureSpec);
        int wSize = MeasureSpec.getSize(widthMeasureSpec);
        if (wMode != MeasureSpec.UNSPECIFIED) want = Math.min(want, wSize);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int hSize = MeasureSpec.getSize(heightMeasureSpec);
        if (hMode == MeasureSpec.EXACTLY || hMode == MeasureSpec.AT_MOST) want = Math.min(want, hSize);
        setMeasuredDimension(want, want);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float size = Math.min(w, h);
        cx = w / 2f;
        cy = h / 2f;
        ringStroke = Math.max(dp(3), size * 0.014f);
        ringR = size / 2f - ringStroke - dp(4);
        haloMax = size / 2f;
        coreMax = ringR * 0.64f;
        coreMin = ringR * 0.46f;

        rimPaint.setStrokeWidth(Math.max(1f, dp(1) * 1.2f));
        wavePaint.setStrokeWidth(Math.max(1f, dp(1)));

        // 字級：照系統字體大小，但不能大過球心
        float cueSize = Math.min(sp(18), coreMin * 0.34f);
        cuePaint.setTextSize(cueSize);
        Paint.FontMetrics cueFm = cuePaint.getFontMetrics();
        cueBaseline = cy - (cueFm.ascent + cueFm.descent) / 2f;

        buildShaders();
        sized = true;
    }

    private void buildShaders() {
        if (haloMax <= 0 || coreMax <= 0) return;
        haloPaint.setShader(new RadialGradient(cx, cy, haloMax,
                new int[]{withAlpha(accent, 0x55), withAlpha(accent, 0x22), withAlpha(accent, 0x00)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        corePaint.setShader(new RadialGradient(cx, cy - coreMax * 0.18f, coreMax * 1.05f,
                new int[]{withAlpha(accentLight, 0x70), withAlpha(accent, 0x46), withAlpha(accent, 0x18)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
    }

    private void applyColors() {
        rimPaint.setColor(withAlpha(accentLight, 0x88));
        wavePaint.setColor(accentLight);
        cuePaint.setColor(textColor);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (running) invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    // ---------------------------------------------------------------- draw

    @Override
    protected void onDraw(Canvas canvas) {
        if (!sized) return;

        // 1) 呼吸值 b：0 = 完全吐氣，1 = 完全吸氣
        long phase = 0;
        boolean inhale = true;
        float b;
        if (animate) {
            phase = (SystemClock.uptimeMillis() - breathStart) % CYCLE_MS;
            inhale = phase < HALF_BREATH_MS;
            float local = (inhale ? phase : phase - HALF_BREATH_MS) / (float) HALF_BREATH_MS;
            float eased = (float) ((1 - Math.cos(Math.PI * local)) / 2.0);
            b = inhale ? eased : 1f - eased;
        } else {
            b = 0.6f;
        }

        // 2) 外層光暈
        float haloScale = 0.70f + 0.30f * b;
        canvas.save();
        canvas.scale(haloScale, haloScale, cx, cy);
        canvas.drawCircle(cx, cy, haloMax, haloPaint);
        canvas.restore();

        // 3) 向外擴散的細波紋：吸氣時撐開、越外越淡
        float coreR = coreMin + (coreMax - coreMin) * b;
        for (int i = 1; i <= RING_COUNT; i++) {
            float spread = (ringR * 0.93f - coreR) * (i / (float) RING_COUNT) * (0.35f + 0.65f * b);
            float r = coreR + spread;
            int a = (int) ((0x3A - i * 0x0C) * (0.4f + 0.6f * b));
            wavePaint.setAlpha(Math.max(0, a));
            canvas.drawCircle(cx, cy, r, wavePaint);
        }

        // 4) 球心
        float coreScale = coreR / coreMax;
        canvas.save();
        canvas.scale(coreScale, coreScale, cx, cy);
        canvas.drawCircle(cx, cy, coreMax, corePaint);
        canvas.restore();
        canvas.drawCircle(cx, cy, coreR, rimPaint);

        // 5) 吸氣 / 吐氣 提示字，交界處淡出淡入
        if (animate) {
            long local = inhale ? phase : phase - HALF_BREATH_MS;
            float fade = Math.min(1f, Math.min(local / (float) CUE_FADE_MS,
                    (HALF_BREATH_MS - local) / (float) CUE_FADE_MS));
            cuePaint.setAlpha((int) (0xEE * Math.max(0f, fade)));
            canvas.drawText(inhale ? inhaleText : exhaleText, cx, cueBaseline, cuePaint);
        } else {
            cuePaint.setAlpha(0xDD);
            canvas.drawText(stillText, cx, cueBaseline, cuePaint);
        }

        if (running && animate) postInvalidateOnAnimation();
    }

    // ---------------------------------------------------------------- utils

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }
}
