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
	public void resetRoomView() {
		renderer.rotX = 0.0f;
		renderer.rotY = 0.0f;
		renderer.scaleFactor = 1.0f;
	}

	public void zoomRoomIn() {
		renderer.scaleFactor = Math.min(10.0f, renderer.scaleFactor * 1.2f);
	}

	public void zoomRoomOut() {
		renderer.scaleFactor = Math.max(0.1f, renderer.scaleFactor * 0.8f);
	}

	private float initialPinchSpan = 0f;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        int action = event.getAction() & MotionEvent.ACTION_MASK;
        int pointerCount = event.getPointerCount();

        float sumX = 0, sumY = 0;
        for (int i = 0; i < pointerCount; i++) {
            sumX += event.getX(i);
            sumY += event.getY(i);
        }
        float focusX = sumX / pointerCount;
        float focusY = sumY / pointerCount;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastX = focusX;
                lastY = focusY;
                isMultiTouching = false;
                initialPinchSpan = 0f;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                lastX = focusX;
                lastY = focusY;
                isMultiTouching = true;
                if (pointerCount == 2) {
                    initialPinchSpan = scaleDetector.getCurrentSpan();
                }
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = focusX - lastX;
                float dy = focusY - lastY;

                if (renderer != null) {
                    if (pointerCount == 1 && !isMultiTouching) {
                        
                        if (renderer.currentTouchMode == renderer.TOUCH_MODE_ROOM) {
                            renderer.rotY += dx * 0.5f;
                            renderer.rotX += dy * 0.5f;
                        } 
                        else if (renderer.currentTouchMode == renderer.TOUCH_MODE_OBJ_MOVE) {
                            EditorObject active = renderer.getActiveObject();
                            if (active != null) {
                                
                                float moveSpeed = 0.01f / Math.max(0.5f, renderer.scaleFactor);
                                active.offsetX += dx * moveSpeed;
                                active.offsetY -= dy * moveSpeed;
                            }
                        } 
                        else if(renderer.currentTouchMode == renderer.TOUCH_MODE_OBJ_ROT) {
                            renderer.rotateActiveObjectScreenAligned(dx, dy);
                        }
                        requestRender();
                    } 
                    else if (pointerCount == 2) {
                      
                        float currentSpan = scaleDetector.getCurrentSpan();
                        boolean isPinching = Math.abs(currentSpan - initialPinchSpan) > 25.0f;

                        if (!isPinching) {
                            renderer.rotY += dx * 0.4f;
                            renderer.rotX += dy * 0.4f;
                            requestRender();
                        }
                    } 
                    else if (pointerCount >= 3) {
                        
                        renderer.rotY += dx * 0.4f;
                        renderer.rotX += dy * 0.4f;
                        renderer.rotateActiveObjectScreenAligned(dx, dy);
                        requestRender();
                    }
                }

                lastX = focusX;
                lastY = focusY;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                int upIndex = event.getActionIndex();
                if (pointerCount > 1) {
                    float remX = 0, remY = 0;
                    for (int i = 0; i < pointerCount; i++) {
                        if (i == upIndex) continue;
                        remX += event.getX(i);
                        remY += event.getY(i);
                    }
                    lastX = remX / (pointerCount - 1);
                    lastY = remY / (pointerCount - 1);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isMultiTouching = false;
                initialPinchSpan = 0f;
                lastX = focusX;
                lastY = focusY;
                break;
        }
        return true;
    }
	
	
	
}
