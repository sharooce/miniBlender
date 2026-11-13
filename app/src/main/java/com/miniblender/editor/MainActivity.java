package com.miniblender.editor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;

public class MainActivity extends Activity {

    private static final int REQUEST_PERMISSION_STORAGE = 100;
    private static final int REQUEST_PICK_TEXTURE = 101;
    private static final int REQUEST_PICK_MODEL = 102;
    private static final int REQUEST_PICK_MTL = 103;

    private MyGLSurfaceView glView;
    private TextView tvOverlayInfo;

    private Button btnMainFab;
    private FrameLayout floatingMenuContainer;
    private LinearLayout editControlsLayout;
    private Button btnToggleMode;
    private Button btnTouchModeSwitch;
    private boolean isMenuExpanded = false;
    private String pendingExportFormat = ".3d";
    private boolean isInitialLaunch = true;

    private LinearLayout controlPanel;
    private Button btnTogglePanel;
    private boolean isControlPanelVisible = true;
    private Button btnToggleInfo;
    private boolean isOverlayInfoVisible = true;

    private ScaleGestureDetector scaleGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
            new android.view.View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    if ((visibility & android.view.View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        enableFullscreen();
                    }
                }
            }
        );

        final float density = getResources().getDisplayMetrics().density;

        FrameLayout viewportContainer = new FrameLayout(this);

        glView = new MyGLSurfaceView(this);

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
				@Override
				public boolean onScale(ScaleGestureDetector detector) {
					MyGLRenderer renderer = glView.getRenderer();
					if (renderer != null) {
						renderer.scaleFactor *= detector.getScaleFactor();
						renderer.scaleFactor = Math.max(0.1f, Math.min(renderer.scaleFactor, 10.0f));
						glView.requestRender();
					}
					return true;
				}
			});

        viewportContainer.addView(glView, new FrameLayout.LayoutParams(
									  FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // --- 1. Overlay Info & Tombol Toggle Info ('i') di Pojok Kiri Atas ---
        tvOverlayInfo = new TextView(this);
        tvOverlayInfo.setTextColor(Color.parseColor("#00FFCC"));
        tvOverlayInfo.setTextSize(11f);
        tvOverlayInfo.setPadding(20, 14, 20, 14);

        GradientDrawable infoBg = new GradientDrawable();
        infoBg.setColor(Color.parseColor("#6618181F"));
        infoBg.setCornerRadius(16f);
        infoBg.setStroke(1, Color.parseColor("#33FFFFFF"));
        tvOverlayInfo.setBackground(infoBg);

        int infoBtnSize = (int) (32 * density);

        btnToggleInfo = new Button(this);
        btnToggleInfo.setText("i");
        btnToggleInfo.setTextSize(12f);
        btnToggleInfo.setAllCaps(false);
        btnToggleInfo.setTextColor(Color.parseColor("#00FFCC"));
        btnToggleInfo.setPadding(0, 0, 0, 0);
        btnToggleInfo.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnToggleInfo.setBackgroundTintList(null);
            btnToggleInfo.setStateListAnimator(null);
        }

        GradientDrawable infoBtnBg = new GradientDrawable();
        infoBtnBg.setColor(Color.parseColor("#6618181F"));
        infoBtnBg.setShape(GradientDrawable.OVAL);
        infoBtnBg.setStroke(2, Color.parseColor("#4400FFCC"));
        btnToggleInfo.setBackground(infoBtnBg);

        FrameLayout.LayoutParams infoBtnParams = new FrameLayout.LayoutParams(infoBtnSize, infoBtnSize);
        infoBtnParams.gravity = Gravity.TOP | Gravity.START;
        infoBtnParams.setMargins(20, 20, 0, 0);
        viewportContainer.addView(btnToggleInfo, infoBtnParams);

        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        infoParams.gravity = Gravity.TOP | Gravity.START;
        infoParams.setMargins(infoBtnSize + 32, 20, 20, 20);
        viewportContainer.addView(tvOverlayInfo, infoParams);

        btnToggleInfo.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					isOverlayInfoVisible = !isOverlayInfoVisible;
					if (tvOverlayInfo != null) {
						tvOverlayInfo.setVisibility(isOverlayInfoVisible ? View.VISIBLE : View.GONE);
					}
					btnToggleInfo.setAlpha(isOverlayInfoVisible ? 1.0f : 0.6f);
				}
			});

        // --- 2. Grup Submenu (Atas) + Tombol Hide Panel & FAB (Bawah) di Pojok Kanan Bawah ---
        int fabSize = (int) (52 * density);
        int miniFabSize = (int) (44 * density);

        final LinearLayout fabGroupContainer = new LinearLayout(this);
        fabGroupContainer.setOrientation(LinearLayout.VERTICAL);
        fabGroupContainer.setGravity(Gravity.END);

        floatingMenuContainer = createScrollableFloatingSubMenu();
        floatingMenuContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams subMenuParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subMenuParams.setMargins(0, 0, 0, (int) (10 * density));
        fabGroupContainer.addView(floatingMenuContainer, subMenuParams);

        LinearLayout fabRow = new LinearLayout(this);
        fabRow.setOrientation(LinearLayout.HORIZONTAL);
        fabRow.setGravity(Gravity.CENTER_VERTICAL);

        btnTogglePanel = new Button(this);
        btnTogglePanel.setText("▼");
        btnTogglePanel.setTextSize(16f);
        btnTogglePanel.setTextColor(Color.WHITE);
        btnTogglePanel.setGravity(Gravity.CENTER);
        btnTogglePanel.setPadding(0, 0, 0, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnTogglePanel.setBackgroundTintList(null);
            btnTogglePanel.setStateListAnimator(null);
        }

        GradientDrawable toggleBg = new GradientDrawable();
        toggleBg.setColor(Color.parseColor("#551E1E28"));
        toggleBg.setShape(GradientDrawable.OVAL);
        toggleBg.setStroke(2, Color.parseColor("#55FFFFFF"));
        btnTogglePanel.setBackground(toggleBg);

        LinearLayout.LayoutParams toggleBtnParams = new LinearLayout.LayoutParams(miniFabSize, miniFabSize);
        toggleBtnParams.setMargins(0, 0, (int) (10 * density), 0);
        fabRow.addView(btnTogglePanel, toggleBtnParams);

        btnMainFab = new Button(this);
        btnMainFab.setText("⚙");
        btnMainFab.setTextSize(20f);
        btnMainFab.setTextColor(Color.WHITE);
        btnMainFab.setGravity(Gravity.CENTER);
        btnMainFab.setPadding(0, 0, 0, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnMainFab.setBackgroundTintList(null);
            btnMainFab.setStateListAnimator(null);
        }

        GradientDrawable fabGradient = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{applyAlpha("#02AAB0", 0x66), applyAlpha("#00CDAC", 0x66)}
        );
        fabGradient.setShape(GradientDrawable.OVAL);
        fabGradient.setStroke(2, Color.parseColor("#66FFFFFF"));
        btnMainFab.setBackground(fabGradient);

        fabRow.addView(btnMainFab, new LinearLayout.LayoutParams(fabSize, fabSize));
        fabGroupContainer.addView(fabRow);

        final FrameLayout.LayoutParams groupParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        groupParams.gravity = Gravity.BOTTOM | Gravity.END;
        groupParams.setMargins(0, 0, 24, (int) (145 * density));
        viewportContainer.addView(fabGroupContainer, groupParams);

        btnMainFab.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					toggleFloatingMenu();
				}
			});

        btnTogglePanel.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					isControlPanelVisible = !isControlPanelVisible;
					if (controlPanel != null) {
						controlPanel.setVisibility(isControlPanelVisible ? View.VISIBLE : View.GONE);
					}
					btnTogglePanel.setText(isControlPanelVisible ? "▼" : "▲");

					int bottomOffset = isControlPanelVisible ? (int) (145 * density) : 24;
					groupParams.setMargins(0, 0, 24, bottomOffset);
					fabGroupContainer.setLayoutParams(groupParams);
				}
			});

        // --- 3. Panel Kontrol Bawah (Glassmorphism + Horizontal Scroll) ---
        controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setPadding(10, 10, 10, 10);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.parseColor("#26121216"));
        panelBg.setCornerRadius(20f);
        panelBg.setStroke(1, Color.parseColor("#33FFFFFF"));
        controlPanel.setBackground(panelBg);

        LinearLayout rowObj = new LinearLayout(this);
        rowObj.setOrientation(LinearLayout.HORIZONTAL);

        Button btnPrevObj = createGlassButton("< OBJ", "#2A2A38", "#1E1E26");
        btnPrevObj.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					glView.getRenderer().switchPrevObject();
					glView.requestRender();
					updateOverlayInfo();
				}
			});

        Button btnNextObj = createGlassButton("OBJ >", "#2A2A38", "#1E1E26");
        btnNextObj.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					glView.getRenderer().switchNextObject();
					glView.requestRender();
					updateOverlayInfo();
				}
			});

        btnTouchModeSwitch = createGlassButton("Touch: Room", "#8E44AD", "#9B59B6");
        btnTouchModeSwitch.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					MyGLRenderer r = glView.getRenderer();
					r.currentTouchMode = (r.currentTouchMode + 1) % 3;

					switch (r.currentTouchMode) {
						case MyGLRenderer.TOUCH_MODE_ROOM:
							btnTouchModeSwitch.setText("Touch: Room");
							break;
						case MyGLRenderer.TOUCH_MODE_OBJ_ROT:
							btnTouchModeSwitch.setText("Touch: Obj Rot");
							break;
						case MyGLRenderer.TOUCH_MODE_OBJ_MOVE:
							btnTouchModeSwitch.setText("Touch: Obj Move");
							break;
					}
				}
			});

        final Button btnViewMode = createGlassButton("All", "#2980B9", "#3498DB");
        btnViewMode.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					MyGLRenderer r = glView.getRenderer();
					r.isSingleViewMode = !r.isSingleViewMode;
					btnViewMode.setText(r.isSingleViewMode ? "Single" : "All");
					glView.requestRender();
					updateOverlayInfo();
				}
			});

        Button btnTexActive = createGlassButton("Tex", "#16A085", "#1ABC9C");
        btnTexActive.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (checkStoragePermission()) openTexturePicker();
				}
			});

        Button btnDelObj = createGlassButton("Del", "#C0392B", "#E74C3C");
        btnDelObj.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					MyGLRenderer r = glView.getRenderer();
					if (r != null) {
						EditorObject active = r.getActiveObject();
						String deletedName = (active != null) ? active.name : "Objek";
						r.removeActiveObject();
						glView.requestRender();
						updateOverlayInfo();
						Toast.makeText(MainActivity.this, deletedName + " dihapus!", Toast.LENGTH_SHORT).show();
					}
				}
			});

        btnToggleMode = createGlassButton("EDIT", "#0072FF", "#00C6FF");
        btnToggleMode.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					MyGLRenderer r = glView.getRenderer();
					r.isEditMode = !r.isEditMode;
					updateEditControlsVisibility(r.isEditMode);
					glView.requestRender();
					updateOverlayInfo();
				}
			});

        Button btnResetView = createGlassButton("Reset View", "#D0392B", "#E34C3C");
        final MyGLRenderer renderer = glView.getRenderer();
        btnResetView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (renderer != null) {
						renderer.resetRoomTransform();
						if (glView != null) {
							glView.requestRender();
						}
					}
				}
			});

        Button btnRemoveVertex = createGlassButton("Remove Vertex", "#FF8008", "#FFC837");
        btnRemoveVertex.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (renderer != null && renderer.isEditMode) {
						EditorObject active = renderer.getActiveObject();
						if (active != null) {
							active.history.saveState(active.mesh, active.selectedVertexIndex);
							active.selectedVertexIndex = active.history.removeVertexWithHistory(active.mesh, active.selectedVertexIndex);
							glView.requestRender();
							updateOverlayInfo();
						}
					}
				}
			});

        Button btnUndoVertex = createGlassButton("Undo Vertex", "#FF8008", "#FFC837");
        btnUndoVertex.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (renderer != null && renderer.isEditMode) {
						EditorObject active = renderer.getActiveObject();
						if (active != null) {
							active.selectedVertexIndex = active.history.undo(active.mesh, active.selectedVertexIndex);
							glView.requestRender();
							updateOverlayInfo();
						}
					}
				}
			});

        Button btnRedoVertex = createGlassButton("Redo Vertex", "#FF8008", "#FFC837");
        btnRedoVertex.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (renderer != null && renderer.isEditMode) {
						EditorObject active = renderer.getActiveObject();
						if (active != null) {
							active.selectedVertexIndex = active.history.redo(active.mesh, active.selectedVertexIndex);
							glView.requestRender();
							updateOverlayInfo();
						}
					}
				}
			});

        rowObj.addView(btnPrevObj);
        rowObj.addView(btnNextObj);
        rowObj.addView(btnResetView);
        rowObj.addView(btnRemoveVertex);
        rowObj.addView(btnUndoVertex);
        rowObj.addView(btnRedoVertex);
        rowObj.addView(btnTouchModeSwitch);
        rowObj.addView(btnViewMode);
        rowObj.addView(btnTexActive);
        rowObj.addView(btnDelObj);
        rowObj.addView(btnToggleMode);

        editControlsLayout = new LinearLayout(this);
        editControlsLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout rowVert = new LinearLayout(this);
        rowVert.setOrientation(LinearLayout.HORIZONTAL);

        Button btnPrevV = createGlassButton("< V", "#2E2E3A", "#1F1F28");
        btnPrevV.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					EditorObject active = glView.getRenderer().getActiveObject();
					if (active != null) {
						if (active.selectedVertexIndex > 0) active.selectedVertexIndex--;
						else active.selectedVertexIndex = Math.max(0, active.mesh.vertices.size() - 1);
					}
					glView.requestRender();
					updateOverlayInfo();
				}
			});

        Button btnNextV = createGlassButton("V >", "#2E2E3A", "#1F1F28");
        btnNextV.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					EditorObject active = glView.getRenderer().getActiveObject();
					if (active != null) {
						if (active.selectedVertexIndex < active.mesh.vertices.size() - 1) active.selectedVertexIndex++;
						else active.selectedVertexIndex = 0;
					}
					glView.requestRender();
					updateOverlayInfo();
				}
			});

        Button btnAddV = createGlassButton("+ Add V", "#11998E", "#38EF7D");
        btnAddV.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showAddVertexDialog();
				}
			});

        rowVert.addView(btnPrevV);
        rowVert.addView(btnNextV);
        rowVert.addView(btnAddV);

        LinearLayout rowDpad = new LinearLayout(this);
        rowDpad.setOrientation(LinearLayout.HORIZONTAL);

        Button btnSubX = createGlassButton("-X", "#2B32B2", "#1488CC");
        Button btnAddX = createGlassButton("+X", "#2B32B2", "#1488CC");
        Button btnSubY = createGlassButton("-Y", "#2B32B2", "#1488CC");
        Button btnAddY = createGlassButton("+Y", "#2B32B2", "#1488CC");
        Button btnSubZ = createGlassButton("-Z", "#2B32B2", "#1488CC");
        Button btnAddZ = createGlassButton("+Z", "#2B32B2", "#1488CC");

        btnSubX.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { glView.getRenderer().moveSelectedVertex(-0.1f, 0f, 0f); glView.requestRender(); updateOverlayInfo(); }
			});
        btnAddX.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { glView.getRenderer().moveSelectedVertex(0.1f, 0f, 0f); glView.requestRender(); updateOverlayInfo(); }
			});
        btnSubY.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { glView.getRenderer().moveSelectedVertex(0f, -0.1f, 0f); glView.requestRender(); updateOverlayInfo(); }
			});
        btnAddY.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { glView.getRenderer().moveSelectedVertex(0f, 0.1f, 0f); glView.requestRender(); updateOverlayInfo(); }
			});
        btnSubZ.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { glView.getRenderer().moveSelectedVertex(0f, 0f, -0.1f); glView.requestRender(); updateOverlayInfo(); }
			});
        btnAddZ.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { glView.getRenderer().moveSelectedVertex(0f, 0f, 0.1f); glView.requestRender(); updateOverlayInfo(); }
			});

        rowDpad.addView(btnSubX);
        rowDpad.addView(btnAddX);
        rowDpad.addView(btnSubY);
        rowDpad.addView(btnAddY);
        rowDpad.addView(btnSubZ);
        rowDpad.addView(btnAddZ);

        editControlsLayout.addView(wrapInHorizontalScroll(rowVert));
        editControlsLayout.addView(wrapInHorizontalScroll(rowDpad));

        controlPanel.addView(wrapInHorizontalScroll(rowObj));
        controlPanel.addView(editControlsLayout);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        panelParams.gravity = Gravity.BOTTOM;
        panelParams.setMargins(12, 0, 12, 12);
        viewportContainer.addView(controlPanel, panelParams);

        setContentView(viewportContainer);
        checkStoragePermission();
        updateOverlayInfo();
    }

    private void enableFullscreen() {
        if (getActionBar() != null) {
            getActionBar().hide();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Izin penyimpanan diberikan! Silakan ulangi export.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Izin penyimpanan ditolak. File tidak dapat disimpan.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private HorizontalScrollView wrapInHorizontalScroll(LinearLayout row) {
        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setFillViewport(true);
        scrollView.addView(row, new FrameLayout.LayoutParams(
							   FrameLayout.LayoutParams.WRAP_CONTENT,
							   FrameLayout.LayoutParams.WRAP_CONTENT
						   ));
        return scrollView;
    }

    private int applyAlpha(String hexColor, int alphaByte) {
        int baseColor = Color.parseColor(hexColor);
        return (baseColor & 0x00FFFFFF) | (alphaByte << 24);
    }

    private Button createGlassButton(String text, String startHex, String endHex) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(10.5f);
        btn.setSingleLine(true);
        btn.setAllCaps(false);
        btn.setGravity(Gravity.CENTER);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btn.setBackgroundTintList(null);
            btn.setStateListAnimator(null);
        }

        float density = getResources().getDisplayMetrics().density;
        btn.setMinWidth((int) (54 * density));
        btn.setMinimumWidth((int) (54 * density));
        btn.setPadding((int) (10 * density), 0, (int) (10 * density), 0);

        GradientDrawable btnBg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{applyAlpha(startHex, 0x4D), applyAlpha(endHex, 0x4D)}
        );
        btnBg.setCornerRadius(12f);
        btnBg.setStroke(1, Color.parseColor("#55FFFFFF"));
        btn.setBackground(btnBg);

        int btnHeight = (int) (36 * density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, btnHeight);
        params.setMargins(3, 3, 3, 3);
        btn.setLayoutParams(params);
        return btn;
    }

    private void updateEditControlsVisibility(boolean isEditMode) {
        if (isEditMode) {
            editControlsLayout.setVisibility(View.VISIBLE);
            btnToggleMode.setText("EDIT");
            GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{applyAlpha("#0072FF", 0x4D), applyAlpha("#00C6FF", 0x4D)}
            );
            btnBg.setCornerRadius(12f);
            btnBg.setStroke(1, Color.parseColor("#55FFFFFF"));
            btnToggleMode.setBackground(btnBg);
        } else {
            editControlsLayout.setVisibility(View.GONE);
            btnToggleMode.setText("VIEW");
            GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{applyAlpha("#444450", 0x4D), applyAlpha("#2C2C35", 0x4D)}
            );
            btnBg.setCornerRadius(12f);
            btnBg.setStroke(1, Color.parseColor("#55FFFFFF"));
            btnToggleMode.setBackground(btnBg);
        }
    }

    private FrameLayout createScrollableFloatingSubMenu() {
        FrameLayout container = new FrameLayout(this);
        int maxHeight = (int) (320 * getResources().getDisplayMetrics().density);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(16, 16, 16, 16);

        GradientDrawable glassBg = new GradientDrawable();
        glassBg.setColor(Color.parseColor("#4418181F"));
        glassBg.setCornerRadius(24f);
        glassBg.setStroke(2, Color.parseColor("#44FFFFFF"));
        panel.setBackground(glassBg);

        // 1. Tombol Open MTL
        Button btnAddCube = createMenuButton("Open Mtl File", "#8E44AD");
        btnAddCube.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					openMtlFilePicker();
				}
			});

        // 2. Tombol Open Model
        Button btnLoadModel = createMenuButton("Open Model File", "#2980B9");
        btnLoadModel.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (checkStoragePermission()) openModelPicker();
				}
			});

        // 3. Tombol Open Texture
        Button btnLoadTex = createMenuButton("Open Texture BMP", "#16A085");
        btnLoadTex.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (checkStoragePermission()) openTexturePicker();
				}
			});

        // 4. Tombol Export .3D
        Button btnExport3D = createMenuButton("Export Model (.3D)", "#D35400");
        btnExport3D.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (checkStoragePermission()) showSaveFileNameDialog(".3d");
				}
			});

        // 5. Tombol Export .MDL
        Button btnExportMDL = createMenuButton("Export Model (.MDL)", "#C0392B");
        btnExportMDL.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (checkStoragePermission()) showSaveFileNameDialog(".mdl");
				}
			});

        // 6. Tombol Export .OBJ
        Button btnExportOBJ = createMenuButton("Export Wavefront (.OBJ)", "#7F8C8D");
        btnExportOBJ.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (checkStoragePermission()) showSaveFileNameDialog(".obj");
				}
			});

        panel.addView(btnAddCube);
        panel.addView(btnLoadModel);
        panel.addView(btnLoadTex);
        panel.addView(btnExport3D);
        panel.addView(btnExportMDL);
        panel.addView(btnExportOBJ);

        scrollView.addView(panel);
        container.addView(scrollView, new FrameLayout.LayoutParams(
							  FrameLayout.LayoutParams.WRAP_CONTENT, maxHeight));

        return container;
    }

    private Button createMenuButton(String text, String hexAccentColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(10.5f);
        btn.setAllCaps(false);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(18, 10, 18, 10);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btn.setBackgroundTintList(null);
            btn.setStateListAnimator(null);
        }

        GradientDrawable btnBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{applyAlpha("#252530", 0x55), applyAlpha(hexAccentColor, 0x55)}
        );
        btnBg.setCornerRadius(14f);
        btnBg.setStroke(1, Color.parseColor("#44FFFFFF"));
        btn.setBackground(btnBg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 4, 0, 4);
        btn.setLayoutParams(params);
        return btn;
    }

    private void toggleFloatingMenu() {
        if (!isMenuExpanded) {
            floatingMenuContainer.setVisibility(View.VISIBLE);

            AnimationSet animSet = new AnimationSet(true);
            animSet.setInterpolator(new AccelerateDecelerateInterpolator());
            animSet.setDuration(180);

            TranslateAnimation slideUp = new TranslateAnimation(0, 0, 30, 0);
            AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
            ScaleAnimation scaleUp = new ScaleAnimation(0.85f, 1.0f, 0.85f, 1.0f,
														Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 1.0f);

            animSet.addAnimation(slideUp);
            animSet.addAnimation(fadeIn);
            animSet.addAnimation(scaleUp);

            floatingMenuContainer.startAnimation(animSet);
            btnMainFab.setText("✕");
        } else {
            AnimationSet animSet = new AnimationSet(true);
            animSet.setInterpolator(new AccelerateDecelerateInterpolator());
            animSet.setDuration(140);

            TranslateAnimation slideDown = new TranslateAnimation(0, 0, 0, 20);
            AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);

            animSet.addAnimation(slideDown);
            animSet.addAnimation(fadeOut);
            animSet.setAnimationListener(new Animation.AnimationListener() {
					@Override public void onAnimationStart(Animation animation) {}
					@Override
					public void onAnimationEnd(Animation animation) {
						floatingMenuContainer.setVisibility(View.GONE);
					}
					@Override public void onAnimationRepeat(Animation animation) {}
				});

            floatingMenuContainer.startAnimation(animSet);
            btnMainFab.setText("⚙");
        }
        isMenuExpanded = !isMenuExpanded;
    }

    private String getCleanFileName(String inputPath) {
        if (inputPath == null) return "Unknown";

        try {
            inputPath = java.net.URLDecoder.decode(inputPath, "UTF-8");
        } catch (Exception ignored) {}

        if (inputPath.contains("/")) {
            inputPath = inputPath.substring(inputPath.lastIndexOf("/") + 1);
        }

        if (inputPath.contains("?")) {
            inputPath = inputPath.substring(0, inputPath.indexOf("?"));
        }
        return inputPath;
    }

    public void updateOverlayInfo() {
        if (glView == null || glView.getRenderer() == null) return;
        MyGLRenderer r = glView.getRenderer();
        EditorObject activeObj = r.getActiveObject();
        if (activeObj == null) return;

        updateEditControlsVisibility(r.isEditMode);

        MeshModel mesh = activeObj.mesh;
        int totalVerts = mesh.vertices.size();
        int totalUVs = mesh.texCoords.size();
        int totalNormals = mesh.normals.size();
        int totalFaces = mesh.triangles.size();
        String modeStr = r.isEditMode ? "EDIT" : "VIEW";

        String cleanName = getCleanFileName(activeObj.name);

        StringBuilder sb = new StringBuilder();
        sb.append("❖ miniBlender v1.0 [").append(modeStr).append("]\n");
        sb.append("Obj: ").append(cleanName).append("\n");

        sb.append("v: ").append(totalVerts)
            .append(" | vt: ").append(totalUVs)
            .append(" | vn: ").append(totalNormals)
            .append(" | f: ").append(totalFaces);
        sb.append("\nTexID: ").append(activeObj.textureId);
        if (activeObj.texturePath != null && !activeObj.texturePath.isEmpty()) {
            sb.append(" | File: ").append(getCleanFileName(activeObj.texturePath));
        } else {
            sb.append(" | File: [BELUM DIPILIH]");
        }

        if (r.isEditMode && totalVerts > 0 && activeObj.selectedVertexIndex >= 0 && activeObj.selectedVertexIndex < totalVerts) {
            float[] v = mesh.vertices.get(activeObj.selectedVertexIndex);
            sb.append(String.format("\nActive V[%d]: X:%.2f Y:%.2f Z:%.2f", activeObj.selectedVertexIndex, v[0], v[1], v[2]));
        }
        if (activeObj != null) {
            String infoText = "\nObj: " + activeObj.name +
                "\nV: " + activeObj.mesh.vertices.size() + " | F: " + activeObj.mesh.triangles.size() +
                "\nPath: " + (activeObj.modelPath.isEmpty() ? "-" : activeObj.modelPath) +
                "\nStatus: " + activeObj.mtlStatus;
            sb.append(infoText);
        }

        tvOverlayInfo.setText(sb.toString());
    }

    private void showSaveFileNameDialog(final String extension) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Simpan Objek Aktif (" + extension + ")");
        final EditText input = new EditText(this);
        EditorObject active = glView.getRenderer().getActiveObject();

        String defaultName = (active != null) ? getCleanFileName(active.name) : "model";
        if (defaultName.contains(".")) {
            defaultName = defaultName.substring(0, defaultName.lastIndexOf("."));
        }
        input.setText(defaultName + extension);
        builder.setView(input);

        builder.setPositiveButton("Pilih Folder & Simpan", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String fileName = input.getText().toString().trim();
					if (fileName.isEmpty()) fileName = "untitled" + extension;
					if (!fileName.endsWith(extension)) fileName = fileName + extension;
					pendingExportFormat = extension;

					Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
					intent.addCategory(Intent.CATEGORY_OPENABLE);

					if (extension.equals(".mdl")) {
						intent.setType("application/octet-stream");
					} else {
						intent.setType("*/*");
					}

					intent.putExtra(Intent.EXTRA_TITLE, fileName);
					startActivityForResult(intent, 103);
				}
			});
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private void showAddObjectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Tambah Cube Manual");

        final EditText input = new EditText(this);
        input.setHint("Nama Objek");
        input.setText("Cube_" + (glView.getRenderer().getObjects().size() + 1));
        builder.setView(input);

        builder.setPositiveButton("Tambah", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String name = input.getText().toString().trim();
					if (name.isEmpty()) name = "Cube_" + System.currentTimeMillis();
					glView.getRenderer().addNewCubeObject(name);
					glView.requestRender();
					updateOverlayInfo();
				}
			});
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private void showAddVertexDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Tambah Vertex Baru");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        final EditText inputX = new EditText(this); inputX.setHint("X"); inputX.setText("0.0"); layout.addView(inputX);
        final EditText inputY = new EditText(this); inputY.setHint("Y"); inputY.setText("1.0"); layout.addView(inputY);
        final EditText inputZ = new EditText(this); inputZ.setHint("Z"); inputZ.setText("0.0"); layout.addView(inputZ);

        builder.setView(layout);
        builder.setPositiveButton("Tambah", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					try {
						float x = Float.parseFloat(inputX.getText().toString());
						float y = Float.parseFloat(inputY.getText().toString());
						float z = Float.parseFloat(inputZ.getText().toString());

						EditorObject active = glView.getRenderer().getActiveObject();
						if (active != null) {
							int newIndex = active.mesh.addNewVertex(x, y, z);
							active.selectedVertexIndex = newIndex;
							glView.requestRender();
							updateOverlayInfo();
						}
					} catch (Exception e) {
						Toast.makeText(MainActivity.this, "Input tidak valid!", Toast.LENGTH_SHORT).show();
					}
				}
			});
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                    );
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(
                        android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    );
                    startActivity(intent);
                }
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
									   Manifest.permission.READ_EXTERNAL_STORAGE,
									   Manifest.permission.WRITE_EXTERNAL_STORAGE
								   }, 101);
                return false;
            }
        }
        return true;
    }

    private void openTexturePicker() {
        toggleFloatingMenu();
        isInitialLaunch = false;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Pilih Tekstur"), REQUEST_PICK_TEXTURE);
    }

    private void openModelPicker() {
        toggleFloatingMenu();
        isInitialLaunch = false;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Buka File Model (.3d/.obj/.mdl)"), REQUEST_PICK_MODEL);
    }

    public void openMtlFilePicker() {
        EditorObject active = glView.getRenderer().getActiveObject();
        if (active == null) {
            Toast.makeText(this, "Tidak ada objek aktif!", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_PICK_MTL);
    }

    private Bitmap decodeBitmapSafe(Uri uri) {
        try {
            // 1. Coba decoder bawaan Android dulu
            InputStream is = getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bmp = BitmapFactory.decodeStream(is, null, options);
            if (is != null) is.close();
            if (bmp != null) return bmp;

            // 2. Jika null (misal BMP 8-bit palette / header non-standar), baca manual byte BMP
            InputStream rawIs = getContentResolver().openInputStream(uri);
            if (rawIs == null) return null;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = rawIs.read(buf)) != -1) baos.write(buf, 0, n);
            rawIs.close();
            byte[] data = baos.toByteArray();

            if (data.length > 54 && data[0] == 'B' && data[1] == 'M') {
                int dataOffset = (data[10] & 0xFF) | ((data[11] & 0xFF) << 8) | ((data[12] & 0xFF) << 16) | ((data[13] & 0xFF) << 24);
                int width = (data[18] & 0xFF) | ((data[19] & 0xFF) << 8) | ((data[20] & 0xFF) << 16) | ((data[21] & 0xFF) << 24);
                int height = (data[22] & 0xFF) | ((data[23] & 0xFF) << 8) | ((data[24] & 0xFF) << 16) | ((data[25] & 0xFF) << 24);
                int bpp = (data[28] & 0xFF) | ((data[29] & 0xFF) << 8);

                boolean topDown = height < 0;
                int absH = Math.abs(height);
                int[] pixels = new int[width * absH];

                if (bpp == 24) {
                    int rowSize = ((width * 3 + 3) / 4) * 4;
                    for (int y = 0; y < absH; y++) {
                        int srcY = topDown ? y : (absH - 1 - y);
                        int rowStart = dataOffset + srcY * rowSize;
                        for (int x = 0; x < width; x++) {
                            int idx = rowStart + x * 3;
                            if (idx + 2 < data.length) {
                                int b = data[idx] & 0xFF;
                                int g = data[idx + 1] & 0xFF;
                                int r = data[idx + 2] & 0xFF;
                                pixels[y * width + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                            }
                        }
                    }
                    return Bitmap.createBitmap(pixels, width, absH, Bitmap.Config.ARGB_8888);
                } else if (bpp == 8) {
                    int headerSize = (data[14] & 0xFF) | ((data[15] & 0xFF) << 8) | ((data[16] & 0xFF) << 16) | ((data[17] & 0xFF) << 24);
                    int paletteOffset = 14 + headerSize;
                    int[] palette = new int[256];
                    for (int i = 0; i < 256; i++) {
                        int pIdx = paletteOffset + i * 4;
                        if (pIdx + 2 < data.length) {
                            int b = data[pIdx] & 0xFF;
                            int g = data[pIdx + 1] & 0xFF;
                            int r = data[pIdx + 2] & 0xFF;
                            palette[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
                        }
                    }
                    int rowSize = ((width + 3) / 4) * 4;
                    for (int y = 0; y < absH; y++) {
                        int srcY = topDown ? y : (absH - 1 - y);
                        int rowStart = dataOffset + srcY * rowSize;
                        for (int x = 0; x < width; x++) {
                            int idx = rowStart + x;
                            if (idx < data.length) {
                                pixels[y * width + x] = palette[data[idx] & 0xFF];
                            }
                        }
                    }
                    return Bitmap.createBitmap(pixels, width, absH, Bitmap.Config.ARGB_8888);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            final Uri uri = data.getData();

            if (requestCode == REQUEST_PICK_TEXTURE) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    final Bitmap bitmap = decodeBitmapSafe(uri);
                    if (inputStream != null) inputStream.close();

                    if (bitmap != null) {
                        glView.queueEvent(new Runnable() {
								@Override
								public void run() {
									glView.getRenderer().loadTextureForActiveObject(bitmap, uri.toString());
									runOnUiThread(new Runnable() {
											@Override
											public void run() {
												glView.requestRender();
												updateOverlayInfo();
												EditorObject active = glView.getRenderer().getActiveObject();
												Toast.makeText(MainActivity.this, "Tekstur terpasang ke: " + active.name, Toast.LENGTH_SHORT).show();
												glView.getRenderer().saveSceneToCache();
											}
										});
								}
							});
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Error tekstur: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == REQUEST_PICK_MODEL) {
                try {
                    final String path = uri.toString();
                    glView.queueEvent(new Runnable() {
							@Override
							public void run() {
								try {
									InputStream is = getContentResolver().openInputStream(uri);
									if (is != null) {
										// 1. Load model utama (.obj / .3d / .mdl)
										glView.getRenderer().replaceOrImportModelObject(is, path);
										is.close();

										// 2. Deteksi & load .mtl otomatis jika ekstensi file adalah .obj
										EditorObject active = glView.getRenderer().getActiveObject();
										if (active != null && active.name.toLowerCase().endsWith(".obj")) {
											InputStream mtlStream = null;
											String mtlName = active.name.replaceAll("(?i)\\.obj$", ".mtl");

											// Cek Jalur A: Lewat File Path asli (misal /storage/emulated/0/.../aventador.mtl)
											if (active.modelPath != null && !active.modelPath.isEmpty()) {
												String mtlRealPath = active.modelPath.replaceAll("(?i)\\.obj$", ".mtl");
												File mtlFile = new File(mtlRealPath);
												if (mtlFile.exists() && mtlFile.canRead()) {
													mtlStream = new java.io.FileInputStream(mtlFile);
												}
											}

											// Cek Jalur B (Cadangan): Ganti ekstensi langsung di string Uri content://
											if (mtlStream == null && path.toLowerCase().endsWith(".obj")) {
												try {
													String mtlUriStr = path.replaceAll("(?i)\\.obj$", ".mtl");
													mtlStream = getContentResolver().openInputStream(Uri.parse(mtlUriStr));
												} catch (Exception ignored) {}
											}

											// Jika file .mtl ditemukan dan bisa dibuka, langsung terapkan warnanya!
											if (mtlStream != null) {
												active.applyMTLFromStream(mtlStream, mtlName);
												active.mtlStatus = active.mesh.lastMtlStatus;
												mtlStream.close();
											}
										}

										runOnUiThread(new Runnable() {
												@Override
												public void run() {
													glView.requestRender();
													updateOverlayInfo();
													EditorObject active = glView.getRenderer().getActiveObject();
													Toast.makeText(MainActivity.this, "Berhasil memuat: " + active.name, Toast.LENGTH_SHORT).show();
													glView.getRenderer().saveSceneToCache();
												}
											});
									}
								} catch (Exception e) {
									e.printStackTrace();
									final Exception a = e;
									runOnUiThread(new Runnable() {
											@Override
											public void run() {
												Toast.makeText(MainActivity.this, "Gagal parsing model: " + a.getMessage(), Toast.LENGTH_LONG).show();
											}
										});
								}
							}
						});
                } catch (Exception e) {
                    Toast.makeText(this, "Error akses file model: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        glView.onPause();

        if (glView != null) {
            glView.getRenderer().saveAllObjectsToCache(this, glView.getRenderer().activeObjectIndex);
        }
        enableFullscreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glView.onResume();

        if (isInitialLaunch && glView != null && glView.getRenderer() != null) {
            isInitialLaunch = false;
            glView.queueEvent(new Runnable() {
					@Override
					public void run() {
						glView.getRenderer().loadAllObjectsFromCache(getApplicationContext());
						runOnUiThread(new Runnable() {
								@Override
								public void run() {
									glView.requestRender();
									updateOverlayInfo();
								}
							});
					}
				});
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableFullscreen();
        }
    }
}

