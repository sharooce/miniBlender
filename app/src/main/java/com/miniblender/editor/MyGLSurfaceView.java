package com.miniblender.editor;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public class MyGLSurfaceView extends GLSurfaceView {

    private final MyGLRenderer renderer;
    private final ScaleGestureDetector scaleDetector;

    private float lastX, lastY;
    private boolean isZooming = false;
	private boolean isMultiTouching = false;

    public MyGLSurfaceView(Context context) {
        super(context);

        setEGLContextClientVersion(2);
        renderer = new MyGLRenderer(context);
        setRenderer(renderer);

        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (renderer != null) {
                    renderer.scaleFactor *= detector.getScaleFactor();
                    renderer.scaleFactor = Math.max(0.1f, Math.min(renderer.scaleFactor, 10.0f));
                    requestRender();
                }
                return true;
            }
        });
    }

    public MyGLRenderer getRenderer() {
        return renderer;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        int action = event.getAction() & MotionEvent.ACTION_MASK;
        int pointerCount = event.getPointerCount();
        float x = event.getX();
        float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                lastX = x;
                lastY = y;
                isZooming = (pointerCount > 1);
				if (pointerCount > 1) {
                    isMultiTouching = true;
                }
               
                break;

            case MotionEvent.ACTION_MOVE:
				float dx = x - lastX;
				float dy = y - lastY;
                if (pointerCount > 1 || scaleDetector.isInProgress()) {
                    isZooming = true;
                    lastX = x;
                    lastY = y;
                    return true;
                }
				if (pointerCount == 2 && !scaleDetector.isInProgress()) {
					EditorObject active = renderer.getActiveObject();
					if (active != null) {
						// Sinkronkan rotasi dengan sudut kamera saat ini agar selaras
						double rad = Math.toRadians(renderer.rotY);
						float cos = (float) Math.cos(rad);
						float sin = (float) Math.sin(rad);

						active.rotY += (dx * cos - dy * sin) * 0.4f;
						active.rotX += (dy * cos + dx * sin) * 0.4f;
					}
					requestRender();
				}
				// -------------------------------------------------------------
				// 2. TIGA JARI (TRIPLE TOUCH): ROTATE ROOM / KAMERA
				// -------------------------------------------------------------
				else if (pointerCount >= 3) {
					renderer.rotY += dx * 0.4f;
					renderer.rotX += dy * 0.4f;
					requestRender();
				}

                if (!isZooming) {
                    

                    if (renderer != null) {
                        if (renderer.currentTouchMode == renderer.TOUCH_MODE_ROOM) {
                            // Mode 1: Rotasi Ruangan / Kamera
                            renderer.rotY += dx * 0.5f;
                            renderer.rotX += dy * 0.5f;
                        } 
                        else if (renderer.currentTouchMode == renderer.TOUCH_MODE_OBJ_MOVE) {
                            // Mode 2: Move Objek (Aman dari Null)
                            EditorObject active = renderer.getActiveObject();
                            if (active != null) {
                                active.offsetX += dx * 0.01f;
                                active.offsetY -= dy * 0.01f;
                            }
                        } 
                        else if (renderer.currentTouchMode == renderer.TOUCH_MODE_OBJ_ROT) {
                            // Mode 3: Rotasi Objek Aktif
                            EditorObject active = renderer.getActiveObject();
                            if (active != null) {
                                // Sinkronkan rotasi dengan arah pandang kamera saat ini
                                double rad = Math.toRadians(renderer.rotY);
                                float cos = (float) Math.cos(rad);
                                float sin = (float) Math.sin(rad);

                                active.rotY += (dx * cos - dy * sin) * 0.5f;
                                active.rotX += (dy * cos + dx * sin) * 0.5f;
                            }
                        }
                        requestRender();
                    }
                    lastX = x;
                    lastY = y;
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                isZooming = true;
                lastX = x;
                lastY = y;
                break;

            case MotionEvent.ACTION_UP:
                isZooming = false;
                lastX = x;
                lastY = y;
                break;
        }
        return true;
    }
}
