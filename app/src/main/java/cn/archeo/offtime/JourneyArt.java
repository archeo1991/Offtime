package cn.archeo.offtime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Local vector-like artwork: no bitmap downloads, and it scales to the available screen. */
final class JourneyArt {
    private static final int INK = 0xff202d2b, GREEN = 0xff496f60, PALE = 0xffdce7db;
    private static final int WARM = 0xffd79a70, MUTED = 0xff63716d;
    private static final int[] GROUND = {0xffe4e9d6, 0xffdce6da, 0xffd9e9e5, 0xffeadfd0};
    private JourneyArt() { }

    static float x(float t) { return .50f + .25f * (float) Math.sin(t * Math.PI * 2.8f); }
    static float y(float t) { return .93f - .86f * t; }

    static final class MapView extends View {
        private final Paint p = new Paint(3);
        private final int steps;
        private final OnStop tap;
        private final float[] nodes = {24f / 240, 72f / 240, 144f / 240, 1f};
        interface OnStop { void show(int index); }
        MapView(Context c, int steps, OnStop tap) {
            super(c); this.steps = steps; this.tap = tap;
            setContentDescription("林间小径，已前进 " + steps + " 格。四处地标在下方可逐项点按。");
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight(), scale = getResources().getDisplayMetrics().density;
            p.setColor(0xffedf0e8); canvas.drawRoundRect(new RectF(0, 0, w, h), 24 * scale, 24 * scale, p);
            p.setColor(0xfff7f5ea); canvas.drawCircle(w * .8f, h * .12f, 58 * scale, p);
            for (int i = 0; i < 29; i++) {
                float t = (i + 1f) / 30;
                float side = i % 2 == 0 ? -.30f : .29f;
                tree(canvas, w * Math.max(.08f, Math.min(.92f, x(t) + side)), h * y(t),
                        (i % 3 == 0 ? 12 : 9) * scale, i % 3 == 0 ? 0xffa9bdab : 0xffc4d4bd);
            }
            Path road = route(w, h);
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(12 * scale); p.setColor(0xffffffff); canvas.drawPath(road, p);
            p.setStrokeWidth(4 * scale); p.setColor(0xffaebeb0);
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{8 * scale, 10 * scale}, 0));
            canvas.drawPath(road, p); p.setPathEffect(null);
            Path walked = route(w, h, steps);
            p.setStrokeWidth(5 * scale); p.setColor(GREEN); canvas.drawPath(walked, p);
            p.setStyle(Paint.Style.FILL);
            for (int i = nodes.length - 1; i >= 0; i--) {
                float t = nodes[i], nx = w * x(t), ny = h * y(t);
                boolean open = steps >= Journey.STOPS[i];
                p.setColor(0xffffffff); canvas.drawCircle(nx, ny, 16 * scale, p);
                p.setColor(open ? GREEN : 0xffa5ada8); canvas.drawCircle(nx, ny, 11 * scale, p);
                p.setColor(0xffffffff); canvas.drawCircle(nx, ny, 3 * scale, p);
                float labelX = i % 2 == 0 ? w * .04f : w * .52f;
                float labelY = Math.max(37 * scale, Math.min(h - 42 * scale, ny - 29 * scale));
                p.setColor(open ? 0xffffffff : 0xfff3f4ef);
                canvas.drawRoundRect(new RectF(labelX, labelY, labelX + w * .43f, labelY + 51 * scale), 12 * scale, 12 * scale, p);
                p.setColor(INK); p.setTextSize(14 * scale); p.setFakeBoldText(true);
                canvas.drawText(Journey.NAMES[i], labelX + 10 * scale, labelY + 21 * scale, p);
                p.setFakeBoldText(false); p.setTextSize(11 * scale); p.setColor(open ? GREEN : MUTED);
                canvas.drawText(open ? "已抵达 · 查看" : "还差 " + (Journey.STOPS[i] - steps) + " 格", labelX + 10 * scale, labelY + 39 * scale, p);
            }
            float travellerX = w * x(steps / 240f), travellerY = h * y(steps / 240f);
            p.setColor(0xffffffff); canvas.drawCircle(travellerX, travellerY, 13 * scale, p);
            p.setColor(WARM); canvas.drawCircle(travellerX, travellerY, 8 * scale, p);
            p.setTextSize(12 * scale); p.setColor(INK); p.setFakeBoldText(true);
            canvas.drawText("起点", w * .53f, h * .98f, p); p.setFakeBoldText(false);
        }
        private Path route(float w, float h) { return route(w, h, 240); }
        private Path route(float w, float h, int through) {
            Path path = new Path();
            path.moveTo(w * x(0), h * y(0));
            for (int i = 1; i <= through; i++) { float t = i / 240f; path.lineTo(w * x(t), h * y(t)); }
            return path;
        }
        private void tree(Canvas c, float x, float y, float r, int color) {
            p.setColor(0xffbcafa1); c.drawRoundRect(new RectF(x - r * .13f, y, x + r * .13f, y + r), r / 5, r / 5, p);
            p.setColor(color); c.drawCircle(x, y - r * .2f, r, p); c.drawCircle(x - r * .5f, y, r * .72f, p);
            c.drawCircle(x + r * .5f, y, r * .72f, p);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float best = Float.MAX_VALUE; int nearest = -1;
                for (int i = 0; i < nodes.length; i++) {
                    float nx = getWidth() * x(nodes[i]), ny = getHeight() * y(nodes[i]);
                    float dx = event.getX() - nx, dy = event.getY() - ny;
                    float distance = dx * dx + dy * dy;
                    if (distance < best) { best = distance; nearest = i; }
                }
                float labelCenterX = nearest % 2 == 0 ? getWidth() * .255f : getWidth() * .735f;
                float labelY = Math.max(37 * getResources().getDisplayMetrics().density,
                        Math.min(getHeight() - 42 * getResources().getDisplayMetrics().density,
                                getHeight() * y(nodes[nearest]) - 29 * getResources().getDisplayMetrics().density));
                boolean onLabel = Math.abs(event.getX() - labelCenterX) < getWidth() * .215f
                        && event.getY() >= labelY && event.getY() <= labelY + 51 * getResources().getDisplayMetrics().density;
                if (onLabel || best <= Math.pow(40 * getResources().getDisplayMetrics().density, 2)) {
                    performClick(); tap.show(nearest); return true;
                }
            }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }

    static final class PostcardView extends View {
        private final int index;
        private final Paint p = new Paint(3);
        PostcardView(Context context, int index) {
            super(context); this.index = index;
            setContentDescription(Journey.NAMES[index] + "的风景插画");
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            p.setColor(GROUND[index]); canvas.drawRoundRect(new RectF(0, 0, w, h), 22, 22, p);
            p.setColor(0xfff8f1df); canvas.drawCircle(w * .78f, h * .24f, h * .14f, p);
            p.setColor(0xffa6baa7);
            for (int n = 0; n < 4; n++) {
                float tx = w * (.15f + .22f * n), ty = h * (.60f + .05f * (n % 2));
                p.setColor(0xff9db49f); canvas.drawCircle(tx, ty, h * .15f, p);
                p.setColor(0xffb9cbb2); canvas.drawCircle(tx - w * .04f, ty - h * .04f, h * .10f, p);
                p.setColor(0xffa89d8a); canvas.drawRect(tx - w * .008f, ty, tx + w * .008f, h * .85f, p);
            }
            p.setColor(0xfff2f0e4); canvas.drawOval(new RectF(w * .06f, h * .74f, w * .94f, h * 1.3f), p);
            if (index == 0) {
                p.setColor(0xff7f9581);
                for (int n = 0; n < 4; n++) canvas.drawRoundRect(new RectF(w * (.22f + n * .13f), h * (.76f + n * .035f), w * (.35f + n * .13f), h * (.84f + n * .035f)), h * .025f, h * .025f, p);
            } else if (index == 1) {
                p.setColor(WARM);
                for (int n = 0; n < 3; n++) {
                    float bx = w * (.25f + n * .22f);
                    p.setStrokeWidth(2); canvas.drawLine(bx, 0, bx, h * .62f, p);
                    canvas.drawCircle(bx, h * .63f, h * .024f, p);
                }
            } else if (index == 2) {
                p.setColor(0xff91c3be); canvas.drawOval(new RectF(w * .07f, h * .68f, w * .94f, h * 1.06f), p);
                p.setColor(0xffb69675); canvas.drawRoundRect(new RectF(w * .21f, h * .68f, w * .79f, h * .77f), 5, 5, p);
                p.setColor(INK); p.setStrokeWidth(3); canvas.drawLine(w * .24f, h * .68f, w * .24f, h * .57f, p);
                canvas.drawLine(w * .76f, h * .68f, w * .76f, h * .57f, p);
            } else {
                p.setColor(WARM); canvas.drawCircle(w * .76f, h * .36f, h * .19f, p);
                p.setColor(0xff687d6d); canvas.drawRoundRect(new RectF(w * .24f, h * .67f, w * .78f, h * .72f), 4, 4, p);
                canvas.drawRect(w * .29f, h * .69f, w * .32f, h * .90f, p);
                canvas.drawRect(w * .70f, h * .69f, w * .73f, h * .90f, p);
            }
        }
    }
}
