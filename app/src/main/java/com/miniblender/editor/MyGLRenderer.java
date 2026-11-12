package com.miniblender.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.*;

public class MyGLRenderer implements GLSurfaceView.Renderer {

    private final Context context;
    private final List<EditorObject> objects = new ArrayList<>();
	public static final int TOUCH_MODE_ROOM = 0;     // Rotasi Ruangan / Kamera
	public static final int TOUCH_MODE_OBJ_ROT = 1;  // Rotasi Objek Aktif
	public static final int TOUCH_MODE_OBJ_MOVE = 2; // Move / Geser Objek Aktif

	public int currentTouchMode = TOUCH_MODE_ROOM;
    public int activeObjectIndex = 0;

    private GridFloor gridFloor;

    // Rotasi Ruangan / Kamera
    public float rotX = 25.0f;
    public float rotY = -45.0f;
    public float posX = 0.0f;
    public float posY = 0.0f;
    public float scaleFactor = 1.0f;

    public boolean isEditMode = true;
    public boolean isSingleViewMode = false; // false = View All, true = View Single


    private boolean programShaderInitialized = false;

    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    public MyGLRenderer(Context context) {
        this.context = context;
        EditorObject defaultObj = new EditorObject("Bodi Main");
        initDefaultCube(defaultObj.mesh);
        objects.add(defaultObj);
		
    }

    private void initDefaultCube(MeshModel mesh) {
        mesh.vertices.clear();
        mesh.triangles.clear();
        mesh.addVertex(-1.0f, -1.0f,  1.0f, 0.0f, 0.0f);
        mesh.addVertex( 1.0f, -1.0f,  1.0f, 1.0f, 0.0f);
        mesh.addVertex( 1.0f,  1.0f,  1.0f, 1.0f, 1.0f);
        mesh.addVertex(-1.0f,  1.0f,  1.0f, 0.0f, 1.0f);
        mesh.addVertex(-1.0f, -1.0f, -1.0f, 0.0f, 0.0f);
        mesh.addVertex( 1.0f, -1.0f, -1.0f, 1.0f, 0.0f);
        mesh.addVertex( 1.0f,  1.0f, -1.0f, 1.0f, 1.0f);
        mesh.addVertex(-1.0f,  1.0f, -1.0f, 0.0f, 1.0f);

        int[] indices = {
            0,1,2, 2,3,0,  1,5,6, 6,2,1,
            5,4,7, 7,6,5,  4,0,3, 3,7,4,
            3,2,6, 6,7,3,  4,5,1, 1,0,4
        };
        for (int i = 0; i < indices.length; i += 3) {
            mesh.triangles.add(new int[]{indices[i], indices[i+1], indices[i+2]});
        }
		mesh.recalculateNormals(); 
		mesh.updateBuffers();    
    }
	public void resetRoomTransform() {
		// Reset sudut pandang kamera/ruangan ke posisi default (0, 0)
		this.rotX = 0.0f;
		this.rotY = 0.0f;
		this.scaleFactor = 1.0f; // Reset zoom kamera ke skala normal
	}

    public List<EditorObject> getObjects() { return objects; }

    public EditorObject getActiveObject() {
        if (objects.isEmpty()) return null;
        if (activeObjectIndex < 0 || activeObjectIndex >= objects.size()) {
            activeObjectIndex = 0;
        }
        return objects.get(activeObjectIndex);
    }

    public void addNewCubeObject(String name) {
        EditorObject obj = new EditorObject(name);
        initDefaultCube(obj.mesh);
        if (programShaderInitialized) {
            obj.mesh.initShader();
            obj.mesh.updateBuffers();
        }
        objects.add(obj);
        activeObjectIndex = objects.size() - 1;
    }

    public void replaceOrImportModelObject(InputStream inputStream, String fileName) throws Exception {
        String objName = "Model_" + (objects.size() + 1);
        if (fileName != null && fileName.contains("/")) {
            objName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }

        EditorObject active = getActiveObject();
        if (active != null && active.name.equalsIgnoreCase("Bodi Main") && objects.size() == 1) {
            active.name = objName;
            active.mesh.importFromFile(inputStream, fileName);
            if (programShaderInitialized) {
                active.mesh.initShader();
                active.mesh.updateBuffers();
            }
        } else {
            EditorObject newObj = new EditorObject(objName);
            newObj.mesh.importFromFile(inputStream, fileName);
            if (programShaderInitialized) {
                newObj.mesh.initShader();
                newObj.mesh.updateBuffers();
            }
            objects.add(newObj);
            activeObjectIndex = objects.size() - 1;
        }
    }

    public void removeActiveObject() {
        if (objects.isEmpty()) return;
        EditorObject activeObj = getActiveObject();
        if (activeObj != null) {
            if (activeObj.textureId > 0) {
                GLES20.glDeleteTextures(1, new int[]{activeObj.textureId}, 0);
                activeObj.textureId = -1;
            }
            activeObj.mesh.vertices.clear();
            activeObj.mesh.triangles.clear();
        }

        if (objects.size() == 1) {
            EditorObject defaultObj = objects.get(0);
            defaultObj.name = "Bodi Main";
            defaultObj.texturePath = "";
            initDefaultCube(defaultObj.mesh);
            defaultObj.mesh.updateBuffers();
            activeObjectIndex = 0;
            return;
        }

        objects.remove(activeObjectIndex);
        if (activeObjectIndex >= objects.size()) {
            activeObjectIndex = objects.size() - 1;
        }
    }

    public void switchNextObject() {
        if (objects.isEmpty()) return;
        activeObjectIndex = (activeObjectIndex + 1) % objects.size();
    }

    public void switchPrevObject() {
        if (objects.isEmpty()) return;
        activeObjectIndex--;
        if (activeObjectIndex < 0) {
            activeObjectIndex = objects.size() - 1;
        }
    }

    public void moveSelectedVertex(float dx, float dy, float dz) {
        EditorObject active = getActiveObject();
        if (active != null && active.selectedVertexIndex >= 0 && active.selectedVertexIndex < active.mesh.vertices.size()) {
            float[] v = active.mesh.vertices.get(active.selectedVertexIndex);
            v[0] += dx; v[1] += dy; v[2] += dz;
            active.mesh.updateBuffers();
        }
    }
	public void saveAllObjectsToCache(Context context, int activeObjectIndex) {
		try {
			JSONObject sceneRoot = new JSONObject();
			sceneRoot.put("activeObjectIndex", activeObjectIndex);

			JSONArray objectsArray = new JSONArray();
			for (EditorObject obj : objects) {
				objectsArray.put(obj.toJSONObject());
			}
			sceneRoot.put("objects", objectsArray);

			SharedPreferences pref = context.getSharedPreferences("MiniBlenderPrefs", Context.MODE_PRIVATE);
			pref.edit().putString("cached_scene_json", sceneRoot.toString()).apply();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

// Load seluruh daftar objects dari SharedPreferences
	public int loadAllObjectsFromCache(Context context) {
		SharedPreferences pref = context.getSharedPreferences("MiniBlenderPrefs", Context.MODE_PRIVATE);
		String jsonString = pref.getString("cached_scene_json", null);
		resetRoomTransform();
		if (jsonString == null || jsonString.isEmpty()) {
			return 0; // Mengembalikan indeks default jika belum ada cache
		}

		try {
			objects.clear();
			JSONObject sceneRoot = new JSONObject(jsonString);
			int activeIndex = sceneRoot.optInt("activeObjectIndex", 0);

			JSONArray objectsArray = sceneRoot.getJSONArray("objects");
			for (int i = 0; i < objectsArray.length(); i++) {
				JSONObject objJson = objectsArray.getJSONObject(i);
				EditorObject obj = new EditorObject();
				obj.mesh.initShader(); // Inisialisasi shader GPU untuk mesh baru
				obj.fromJSONObject(objJson);
				objects.add(obj);
			}

			return activeIndex;
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}
    public void extrudeActiveVertex(float offsetX, float offsetY, float offsetZ) {
        EditorObject active = getActiveObject();
        if (active != null) {
            active.selectedVertexIndex = active.mesh.extrudeFromSelectedVertex(active.selectedVertexIndex, offsetX, offsetY, offsetZ);
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.22f, 0.22f, 0.24f, 1.0f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        for (EditorObject obj : objects) {
            obj.mesh.initShader();
            obj.mesh.updateBuffers();
        }
        programShaderInitialized = true;
        gridFloor = new GridFloor();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;
        android.opengl.Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 1.0f, 100.0f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        android.opengl.Matrix.setLookAtM(viewMatrix, 0, 0.0f, 2.0f, 7.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        // Matrix Ruangan (Kamera Orbit)
        float[] roomMatrix = new float[16];
        android.opengl.Matrix.setIdentityM(roomMatrix, 0);
        android.opengl.Matrix.translateM(roomMatrix, 0, posX, posY, 0.0f);
        android.opengl.Matrix.rotateM(roomMatrix, 0, rotX, 1.0f, 0.0f, 0.0f);
        android.opengl.Matrix.rotateM(roomMatrix, 0, rotY, 0.0f, 1.0f, 0.0f);

        if (gridFloor != null) {
            float[] gridMvp = new float[16];
            float[] tempGrid = new float[16];
            android.opengl.Matrix.multiplyMM(tempGrid, 0, viewMatrix, 0, roomMatrix, 0);
            android.opengl.Matrix.multiplyMM(gridMvp, 0, projectionMatrix, 0, tempGrid, 0);
            gridFloor.draw(gridMvp);
        }

        // Render Semua Objek
        for (int i = 0; i < objects.size(); i++) {
            EditorObject obj = objects.get(i);
            boolean isThisObjectActive = (i == activeObjectIndex);

            // Jika mode Single View aktif, sembunyikan objek lain
            if (isSingleViewMode && !isThisObjectActive) continue;

            float[] scratchModelMatrix = new float[16];
            float[] tempMatrix = new float[16];
            System.arraycopy(roomMatrix, 0, scratchModelMatrix, 0, 16);
            android.opengl.Matrix.scaleM(scratchModelMatrix, 0, scaleFactor, scaleFactor, scaleFactor);

            // Posisi dan Rotasi Lokal masing-masing objek
            android.opengl.Matrix.translateM(scratchModelMatrix, 0, obj.offsetX, obj.offsetY, obj.offsetZ);
            android.opengl.Matrix.rotateM(scratchModelMatrix, 0, obj.rotX, 1.0f, 0.0f, 0.0f);
            android.opengl.Matrix.rotateM(scratchModelMatrix, 0, obj.rotY, 0.0f, 1.0f, 0.0f);

            android.opengl.Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, scratchModelMatrix, 0);
            android.opengl.Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0);

            obj.mesh.draw(mvpMatrix, scratchModelMatrix, obj.textureId, obj.selectedVertexIndex, isEditMode && isThisObjectActive);
        }
    }

    // PASANG TEKSTUR HANYA KE OBJEK YANG SEDANG AKTIF
	public void loadTextureForActiveObject(final Bitmap srcBitmap, final String texturePath) {
        EditorObject active = getActiveObject();
        if (active == null) return;

        if (texturePath != null) {
            active.texturePath = texturePath;
        }

        if (srcBitmap == null || srcBitmap.isRecycled()) {
            android.util.Log.e("MiniBlenderTex", "Gagal memuat bitmap: Bitmap null atau sudah direcycle!");
            return;
        }

        android.util.Log.i("MiniBlenderTex", "Memuat tekstur: " + srcBitmap.getWidth() + "x" + srcBitmap.getHeight());

        // Konversi ke ARGB_8888 agar aman dibaca OpenGL
        Bitmap argbBmp = srcBitmap.copy(Bitmap.Config.ARGB_8888, true);

        // Flip vertikal bitmap agar sesuai dengan koordinat UV OpenGL
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postScale(1.0f, -1.0f);
        final Bitmap flippedBitmap = Bitmap.createBitmap(
            argbBmp, 0, 0, argbBmp.getWidth(), argbBmp.getHeight(), matrix, true
        );
        if (argbBmp != srcBitmap) argbBmp.recycle();

        int[] textureHandles = new int[1];
        GLES20.glGenTextures(1, textureHandles, 0);

        if (textureHandles[0] != 0) {
            if (active.textureId > 0) {
                GLES20.glDeleteTextures(1, new int[]{active.textureId}, 0);
            }
            active.textureId = textureHandles[0];

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, active.textureId);

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            // Masukkan bitmap ke VRAM OpenGL
            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, flippedBitmap, 0);
			GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            android.util.Log.i("MiniBlenderTex", "Berhasil generate TexID: " + active.textureId);
        } else {
            android.util.Log.e("MiniBlenderTex", "Gagal generate OpenGL Texture ID!");
            active.textureId = 1;
        }

        flippedBitmap.recycle();
        if (!srcBitmap.isRecycled()) {
            srcBitmap.recycle();
        }
    }
	
	
	
	

    public void saveSceneToCache() {
        try {
            File cacheFile = new File(context.getCacheDir(), "miniblender_scene_cache.json");
            JSONObject root = new JSONObject();
            root.put("activeObjectIndex", activeObjectIndex);
            root.put("isEditMode", isEditMode);
            root.put("rotX", rotX);
            root.put("rotY", rotY);

            JSONArray objArray = new JSONArray();
            for (EditorObject obj : objects) {
                objArray.put(obj.toJSONObject());
            }
            root.put("objects", objArray);

            FileWriter writer = new FileWriter(cacheFile);
            writer.write(root.toString());
            writer.close();
        } catch (Exception ignored) {}
    }

    public boolean restoreSceneFromCache() {
        try {
            File cacheFile = new File(context.getCacheDir(), "miniblender_scene_cache.json");
            if (!cacheFile.exists()) return false;

            BufferedReader reader = new BufferedReader(new FileReader(cacheFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            this.activeObjectIndex = root.optInt("activeObjectIndex", 0);
            this.isEditMode = root.optBoolean("isEditMode", true);
            this.rotX = (float) root.optDouble("rotX", 25.0);
            this.rotY = (float) root.optDouble("rotY", -45.0);

            JSONArray objArray = root.getJSONArray("objects");
            if (objArray.length() == 0) return false;

            this.objects.clear();
            for (int i = 0; i < objArray.length(); i++) {
                JSONObject objJson = objArray.getJSONObject(i);
                EditorObject obj = new EditorObject(objJson.optString("name", "Object"));
                obj.fromJSONObject(objJson);

                if (programShaderInitialized) {
                    obj.mesh.initShader();
                    obj.mesh.updateBuffers();
                }

                if (!obj.texturePath.isEmpty()) {
                    File texFile = new File(obj.texturePath);
                    if (texFile.exists()) {
                        Bitmap bmp = BitmapFactory.decodeFile(texFile.getAbsolutePath());
                        if (bmp != null) {
                            loadTextureForActiveObject(bmp, obj.texturePath);
                        }
                    }
                }
                this.objects.add(obj);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

