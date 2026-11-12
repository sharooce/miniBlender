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
	

    // Variabel Touch & Scale Gesture
    private float previousX, previousY;
    private ScaleGestureDetector scaleGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        FrameLayout viewportContainer = new FrameLayout(this);
        LinearLayout.LayoutParams viewportParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);

        glView = new MyGLSurfaceView(this);

        // SETUP SCALE GESTURE DETECTOR UNTUK PINCH ZOOM IN / OUT
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
				@Override
				public boolean onScale(ScaleGestureDetector detector) {
					MyGLRenderer renderer = glView.getRenderer();
					if (renderer != null) {
						renderer.scaleFactor *= detector.getScaleFactor();
						// Batasi skala agar tidak terlalu kecil atau terlalu besar
						renderer.scaleFactor = Math.max(0.1f, Math.min(renderer.scaleFactor, 10.0f));
						glView.requestRender();
					}
					return true;
				}
			});

        // CUSTOM TOUCH LISTENER (MENDUKUNG ROTASI ROOM/OBJ & PINCH ZOOM)
		/*
        glView.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					// Tangkap event pinch-zoom terlebih dahulu
					scaleGestureDetector.onTouchEvent(event);

					float x = event.getX();
					float y = event.getY();
					MyGLRenderer renderer = glView.getRenderer();

					// Jika sedang melakukan pinch dengan 2 jari, abaikan rotasi satu jari
					if (event.getPointerCount() > 1) {
						return true;
					}

					switch (event.getAction()) {
						case MotionEvent.ACTION_DOWN:
							previousX = x;
							previousY = y;
							break;
						case MotionEvent.ACTION_MOVE:
							float dx = x - previousX;
							float dy = y - previousY;

							if (renderer != null) {
								if (renderer.currentTouchMode==renderer.TOUCH_MODE_ROOM) {
									renderer.rotY += dx * 0.5f;
									renderer.rotX += dy * 0.5f;
								}
								else if(renderer.currentTouchMode==renderer.TOUCH_MODE_OBJ_MOVE){
									EditorObject active = renderer.getActiveObject();
									active.offsetX += dx * 0.01f;
									active.offsetY -= dy * 0.01f; 
								}
								else if(renderer.currentTouchMode==renderer.TOUCH_MODE_OBJ_ROT){
									EditorObject active = renderer.getActiveObject();
									if (active != null) {
										active.rotY += dx * 0.5f;
										active.rotX += dy * 0.5f;
									}
								}
								glView.requestRender();
							}
							previousX = x;
							previousY = y;
							break;
					}
					return true;
				}
			});
		*/

        viewportContainer.addView(glView, new FrameLayout.LayoutParams(
									  FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        tvOverlayInfo = new TextView(this);
        tvOverlayInfo.setTextColor(Color.parseColor("#00FFCC"));
        tvOverlayInfo.setTextSize(11f);
        tvOverlayInfo.setPadding(20, 14, 20, 14);

        GradientDrawable infoBg = new GradientDrawable();
        infoBg.setColor(Color.parseColor("#B018181F"));
        infoBg.setCornerRadius(16f);
        tvOverlayInfo.setBackground(infoBg);

        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        infoParams.setMargins(20, 20, 20, 20);
        infoParams.gravity = Gravity.TOP | Gravity.START;
        viewportContainer.addView(tvOverlayInfo, infoParams);

        floatingMenuContainer = createScrollableFloatingSubMenu();
        floatingMenuContainer.setVisibility(View.GONE);

        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        menuParams.gravity = Gravity.BOTTOM | Gravity.END;
        menuParams.setMargins(0, 0, 24, 150);
        viewportContainer.addView(floatingMenuContainer, menuParams);

        btnMainFab = new Button(this);
        btnMainFab.setText("⚙");
        btnMainFab.setTextSize(20f);
        btnMainFab.setTextColor(Color.WHITE);

        GradientDrawable fabGradient = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor("#02AAB0"), Color.parseColor("#00CDAC")}
        );
        fabGradient.setShape(GradientDrawable.OVAL);
        btnMainFab.setBackground(fabGradient);

        int fabSize = (int) (52 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(fabSize, fabSize);
        fabParams.gravity = Gravity.BOTTOM | Gravity.END;
        fabParams.setMargins(0, 0, 24, 24);
        viewportContainer.addView(btnMainFab, fabParams);

        btnMainFab.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					toggleFloatingMenu();
				}
			});

        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setPadding(10, 10, 10, 10);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.parseColor("#D9121216"));
        panelBg.setCornerRadius(20f);
        panelBg.setStroke(2, Color.parseColor("#22FFFFFF"));
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
						case r.TOUCH_MODE_ROOM:
							btnTouchModeSwitch.setText("TOUCH: ROOM");
							break;
						case r.TOUCH_MODE_OBJ_ROT:
							btnTouchModeSwitch.setText("TOUCH: OBJ ROT");
							break;
						case r.TOUCH_MODE_OBJ_MOVE:
							btnTouchModeSwitch.setText("TOUCH: OBJ MOVE");
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
			
		Button btnFlipFace = createGlassButton("FLIP FACE", "#D0392B", "#E34C3C");
		
		final MeshModel meshModel=glView.getRenderer().getActiveObject().mesh;
		btnFlipFace.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (meshModel != null) {
						meshModel.switchWindingMode(); // Membalik arah hadap poligon secara instan

						// Tampilkan status aktif di layar (opsional)
						String modeName = meshModel.isCCWMode ? "CCW" : "CW";
						Toast.makeText(MainActivity.this, "Winding Mode: " + modeName, Toast.LENGTH_SHORT).show();

						// Render ulang tampilan 3D
						if (glView != null) {
							glView.requestRender();
						}
					}
				}
			});

        rowObj.addView(btnPrevObj, new LinearLayout.LayoutParams(0, -2, 0.7f));
		rowObj.addView(btnFlipFace);
        rowObj.addView(btnNextObj, new LinearLayout.LayoutParams(0, -2, 0.7f));
        rowObj.addView(btnTouchModeSwitch, new LinearLayout.LayoutParams(0, -2, 1.0f));
        rowObj.addView(btnViewMode, new LinearLayout.LayoutParams(0, -2, 0.8f));
        rowObj.addView(btnTexActive, new LinearLayout.LayoutParams(0, -2, 0.8f));
        rowObj.addView(btnDelObj, new LinearLayout.LayoutParams(0, -2, 0.7f));
        rowObj.addView(btnToggleMode, new LinearLayout.LayoutParams(0, -2, 0.9f));

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

        Button btnExtrudeV = createGlassButton("+ Extrude", "#FF8008", "#FFC837");
        btnExtrudeV.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					glView.getRenderer().extrudeActiveVertex(0.0f, 0.4f, 0.0f);
					glView.requestRender();
					updateOverlayInfo();
				}
			});

        rowVert.addView(btnPrevV, new LinearLayout.LayoutParams(0, -2, 1.0f));
        rowVert.addView(btnNextV, new LinearLayout.LayoutParams(0, -2, 1.0f));
        rowVert.addView(btnAddV, new LinearLayout.LayoutParams(0, -2, 1.2f));
        rowVert.addView(btnExtrudeV, new LinearLayout.LayoutParams(0, -2, 1.4f));

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

        rowDpad.addView(btnSubX, new LinearLayout.LayoutParams(0, -2, 1.0f));
        rowDpad.addView(btnAddX, new LinearLayout.LayoutParams(0, -2, 1.0f));
        rowDpad.addView(btnSubY, new LinearLayout.LayoutParams(0, -2, 1.0f));
        rowDpad.addView(btnAddY, new LinearLayout.LayoutParams(0, -2, 1.0f));
        rowDpad.addView(btnSubZ, new LinearLayout.LayoutParams(0, -2, 1.0f));
        rowDpad.addView(btnAddZ, new LinearLayout.LayoutParams(0, -2, 1.0f));

        editControlsLayout.addView(rowVert);
        editControlsLayout.addView(rowDpad);

        controlPanel.addView(rowObj);
        controlPanel.addView(editControlsLayout);

        mainLayout.addView(viewportContainer, viewportParams);
        mainLayout.addView(controlPanel);

        setContentView(mainLayout);
        checkStoragePermission();
        updateOverlayInfo();
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
	

    private Button createGlassButton(String text, String startHex, String endHex) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(9.0f);
        btn.setPadding(4, 6, 4, 6);

        GradientDrawable btnBg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor(startHex), Color.parseColor(endHex)}
        );
        btnBg.setCornerRadius(14f);
        btnBg.setStroke(1, Color.parseColor("#33FFFFFF"));
        btn.setBackground(btnBg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        params.setMargins(2, 2, 2, 2);
        btn.setLayoutParams(params);
        return btn;
    }

    private void updateEditControlsVisibility(boolean isEditMode) {
        if (isEditMode) {
            editControlsLayout.setVisibility(View.VISIBLE);
            btnToggleMode.setText("EDIT");
            GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#0072FF"), Color.parseColor("#00C6FF")}
            );
            btnBg.setCornerRadius(14f);
            btnToggleMode.setBackground(btnBg);
        } else {
            editControlsLayout.setVisibility(View.GONE);
            btnToggleMode.setText("VIEW");
            GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#444450"), Color.parseColor("#2C2C35")}
            );
            btnBg.setCornerRadius(14f);
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
		glassBg.setColor(Color.parseColor("#E618181F"));
		glassBg.setCornerRadius(24f);
		glassBg.setStroke(2, Color.parseColor("#33FFFFFF"));
		panel.setBackground(glassBg);

		// 1. Tombol Tambah Cube
		Button btnAddCube = createMenuButton("+ Add Cube", "#8E44AD");
		btnAddCube.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showAddObjectDialog();
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

		// Masukkan semua tombol ke dalam panel
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
        btn.setPadding(18, 10, 18, 10);

        GradientDrawable btnBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.parseColor("#252530"), Color.parseColor(hexAccentColor)}
        );
        btnBg.setCornerRadius(14f);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
									   Manifest.permission.READ_EXTERNAL_STORAGE,
									   Manifest.permission.WRITE_EXTERNAL_STORAGE
								   }, REQUEST_PERMISSION_STORAGE);
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
    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == RESULT_OK && data != null && data.getData() != null) {
			final Uri uri = data.getData();

			
			if (requestCode == REQUEST_PICK_TEXTURE) {
				try {
					InputStream inputStream = getContentResolver().openInputStream(uri);
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inScaled = false;
					final Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
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
			} 

			else if (requestCode == REQUEST_PICK_MODEL) {
				try {
					final String path = uri.toString();
					glView.queueEvent(new Runnable() {
							@Override
							public void run() {
								try {
									InputStream is = getContentResolver().openInputStream(uri);
									if (is != null) {
										glView.getRenderer().replaceOrImportModelObject(is, path);
										is.close();
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
									final Exception a=e;
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
			// 3. EXPORT FILE (FOLDER PICKER SAF)
			else if (requestCode == 103) {
				try {
					java.io.OutputStream os = getContentResolver().openOutputStream(uri);
					if (os != null) {
						EditorObject active = glView.getRenderer().getActiveObject();
						if (active != null) {
							String ext = pendingExportFormat.replace(".", "");

							java.io.File tempFile = new java.io.File(getCacheDir(), "temp_export" + pendingExportFormat);
							active.mesh.exportToFile(tempFile, ext);

							java.io.FileInputStream fis = new java.io.FileInputStream(tempFile);
							byte[] buffer = new byte[1024];
							int length;
							while ((length = fis.read(buffer)) > 0) {
								os.write(buffer, 0, length);
							}
							os.flush();
							os.close();
							fis.close();
							tempFile.delete();

							Toast.makeText(this, "Berhasil disimpan ke folder pilihan!", Toast.LENGTH_LONG).show();
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(this, "Gagal menyimpan file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
}

