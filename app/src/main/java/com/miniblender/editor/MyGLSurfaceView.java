package com.miniblender.editor;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public class MyGLSurfaceView extends GLSurfaceView {

    private final MyGLRenderer renderer;

    private static final int TWO_FINGER_NONE = 0;
    private static final int TWO_FINGER_ZOOM = 1;
    private static final int TWO_FINGER_ROTATE_OBJ = 2;

    private int twoFingerMode = TWO_FINGER_NONE;
    private boolean isMultiTouching = false;

    private float downX = 0f, downY = 0f;
    private float lastX = 0f, lastY = 0f;

    private float startMidX = 0f, startMidY = 0f;
    private float startPinchDist = 0f;
    private float prevMidX = 0f, prevMidY = 0f;
    private float prevPinchDist = 0f;

    public MyGLSurfaceView(Context context) {
        super(context);

        setEGLContextClientVersion(2);
        renderer = new MyGLRenderer(context);
        setRenderer(renderer);

        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (renderer == null) return true;

        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                isMultiTouching = false;
                twoFingerMode = TWO_FINGER_NONE;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                isMultiTouching = true;
                if (pointerCount == 2) {
                    twoFingerMode = TWO_FINGER_NONE;
                    startMidX = prevMidX = (event.getX(0) + event.getX(1)) * 0.5f;
                    startMidY = prevMidY = (event.getY(0) + event.getY(1)) * 0.5f;
                    startPinchDist = prevPinchDist = (float) Math.hypot(
                        event.getX(1) - event.getX(0),
                        event.getY(1) - event.getY(0)
                    );
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (pointerCount == 1 && !isMultiTouching) {
                    float x = event.getX();
                    float y = event.getY();
                    float dx = x - lastX;
                    float dy = y - lastY;

                    if (renderer.currentTouchMode == MyGLRenderer.TOUCH_MODE_ROOM) {
                        renderer.rotY += dx * 0.5f;
                        renderer.rotX += dy * 0.5f;
                    } else if (renderer.currentTouchMode == MyGLRenderer.TOUCH_MODE_OBJ_MOVE) {
                        renderer.dragActiveObjectOnScreen(dx, dy);
                    } else if (renderer.currentTouchMode == MyGLRenderer.TOUCH_MODE_OBJ_ROT) {
                        renderer.rotateActiveObjectScreenAligned(dx, dy);
                    }

                    lastX = x;
                    lastY = y;
                    requestRender();

                } else if (pointerCount == 2) {
                    isMultiTouching = true;
                    float curMidX = (event.getX(0) + event.getX(1)) * 0.5f;
                    float curMidY = (event.getY(0) + event.getY(1)) * 0.5f;
                    float curPinchDist = (float) Math.hypot(
                        event.getX(1) - event.getX(0),
                        event.getY(1) - event.getY(0)
                    );

                    if (twoFingerMode == TWO_FINGER_NONE) {
                        float totalMoveMid = (float) Math.hypot(curMidX - startMidX, curMidY - startMidY);
                        float totalDeltaDist = Math.abs(curPinchDist - startPinchDist);

                        if (totalMoveMid > 14f || totalDeltaDist > 14f) {
                            if (totalDeltaDist > totalMoveMid * 1.15f) {
                                twoFingerMode = TWO_FINGER_ZOOM;
                            } else {
                                twoFingerMode = TWO_FINGER_ROTATE_OBJ;
                            }
                        }
                    }

                    if (twoFingerMode == TWO_FINGER_ZOOM) {
                        if (prevPinchDist > 10f) {
                            float rawRatio = curPinchDist / prevPinchDist;
                            rawRatio = Math.max(0.85f, Math.min(1.15f, rawRatio));
                            renderer.scaleFactor = Math.max(0.1f, Math.min(10.0f, renderer.scaleFactor * rawRatio));
                            requestRender();
                        }
                    } else if (twoFingerMode == TWO_FINGER_ROTATE_OBJ) {
                        float dxMid = curMidX - prevMidX;
                        float dyMid = curMidY - prevMidY;
                        renderer.rotateActiveObjectScreenAligned(dxMid * 1.1f, dyMid * 1.1f);
                        requestRender();
                    }

                    prevMidX = curMidX;
                    prevMidY = curMidY;
                    prevPinchDist = curPinchDist;

                } else if (pointerCount >= 3) {
                    float sumX = 0f, sumY = 0f;
                    for (int i = 0; i < pointerCount; i++) {
                        sumX += event.getX(i);
                        sumY += event.getY(i);
                    }
                    float cx = sumX / pointerCount;
                    float cy = sumY / pointerCount;
                    renderer.rotY += (cx - lastX) * 0.4f;
                    renderer.rotX += (cy - lastY) * 0.4f;
                    lastX = cx;
                    lastY = cy;
                    requestRender();
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                twoFingerMode = TWO_FINGER_NONE;
                if (pointerCount == 2) {
                    int remainingIdx = (event.getActionIndex() == 0) ? 1 : 0;
                    lastX = event.getX(remainingIdx);
                    lastY = event.getY(remainingIdx);
                }
                break;

            case MotionEvent.ACTION_UP:
                
                if (!isMultiTouching) {
                    float moveDist = (float) Math.hypot(event.getX() - downX, event.getY() - downY);
                    if (moveDist < 15f) {
                        int picked = renderer.pickObjectAtTouch(event.getX(), event.getY());
                        if (picked != -1 && picked != renderer.activeObjectIndex) {
                            renderer.activeObjectIndex = picked;
                            requestRender();
                            if (getContext() instanceof MainActivity) {
                                ((MainActivity) getContext()).updateOverlayInfo();
                            }
                        }
                    }
                }
                isMultiTouching = false;
                twoFingerMode = TWO_FINGER_NONE;
                break;

            case MotionEvent.ACTION_CANCEL:
                isMultiTouching = false;
                twoFingerMode = TWO_FINGER_NONE;
                break;
        }
        return true;
    }
}

