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
import android.opengl.*;

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
	public int screenWidth = 1;
	public int screeHeight =1;

    public boolean isEditMode = true;
    public boolean isSingleViewMode = false; // false = View All, true = View Single


    private boolean programShaderInitialized = false;

    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    //private final float[] modelMatrix = new float[16];
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
		
		this.rotX = 0.0f;
		this.rotY = 0.0f;
		this.scaleFactor = 1.0f;
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
	
	private String resolveRealFilePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) return "";

        try {
            // Decode %3A -> ':' dan %2F -> '/'
            String decoded = java.net.URLDecoder.decode(rawPath, "UTF-8");

            // Jika formatnya content://.../document/primary:symbian/pys60_games/aventador.obj
            if (decoded.contains("primary:")) {
                String relPath = decoded.substring(decoded.indexOf("primary:") + "primary:".length());
                File realFile = new File(android.os.Environment.getExternalStorageDirectory(), relPath);
                return realFile.getAbsolutePath();
            }
            // Jika formatnya SD Card eksternal: /document/XXXX-XXXX:folder/file.obj
            if (decoded.startsWith("content://") && decoded.contains(":")) {
                String afterScheme = decoded.substring("content://".length());
                if (afterScheme.contains(":")) {
                    String[] parts = afterScheme.split(":", 2);
                    String storageId = parts[0].substring(parts[0].lastIndexOf('/') + 1);
                    return "/storage/" + storageId + "/" + parts[1];
                }
            }
            // Jika formatnya file:///storage/...
            if (decoded.startsWith("file://")) {
                return new File(android.net.Uri.parse(decoded).getPath()).getAbsolutePath();
            }
            return decoded;
        } catch (Exception e) {
            return rawPath;
        }
    }

	public void replaceOrImportModelObject(InputStream inputStream, String fileName) throws Exception {
        String realPath = resolveRealFilePath(fileName);
        String objName = "Model_" + (objects.size() + 1);
        String parentPath = null;

        if (!realPath.isEmpty()) {
            File f = new File(realPath);
            objName = f.getName();
            if (f.getParentFile() != null) {
                parentPath = f.getParentFile().getAbsolutePath();
            }
        }

        EditorObject active = getActiveObject();
        if (active != null && active.name.equalsIgnoreCase("Bodi Main") && objects.size() == 1) {
            active.name = objName;
            active.modelPath = realPath;
            active.mesh.importFromFile(inputStream, objName, parentPath);
            active.mtlStatus = active.mesh.lastMtlStatus;
            if (programShaderInitialized) {
                active.mesh.initShader();
                active.mesh.updateBuffers();
            }
        } else {
            EditorObject newObj = new EditorObject(objName);
            newObj.modelPath = realPath;
            newObj.mesh.importFromFile(inputStream, objName, parentPath);
            newObj.mtlStatus = newObj.mesh.lastMtlStatus;
            if (programShaderInitialized) {
                newObj.mesh.initShader();
                newObj.mesh.updateBuffers();
            }
            objects.add(newObj);
            activeObjectIndex = objects.size() - 1;
        }
		active.updateBoundingRadius();
    }
	
	public void screenToWorldRay(float touchX, float touchY, float[] outOrigin, float[] outDir) {
        float x = (2.0f * touchX) / screenWidth - 1.0f;
        float y = 1.0f - (2.0f * touchY) / screeHeight;

        float[] invVP = new float[16];
        float[] vp = new float[16];
        android.opengl.Matrix.multiplyMM(vp, 0, projectionMatrix, 0, viewMatrix, 0);
        android.opengl.Matrix.invertM(invVP, 0, vp, 0);

        float[] nearPoint = new float[]{x, y, -1.0f, 1.0f};
        float[] farPoint  = new float[]{x, y,  1.0f, 1.0f};
        float[] nearWorld = new float[4];
        float[] farWorld  = new float[4];

        android.opengl.Matrix.multiplyMV(nearWorld, 0, invVP, 0, nearPoint, 0);
        android.opengl.Matrix.multiplyMV(farWorld,  0, invVP, 0, farPoint,  0);

        for (int i = 0; i < 3; i++) {
            nearWorld[i] /= nearWorld[3];
            farWorld[i]  /= farWorld[3];
            outOrigin[i] = nearWorld[i];
            outDir[i]    = farWorld[i] - nearWorld[i];
        }

        // Normalisasi vektor arah ray
        float len = (float) Math.sqrt(outDir[0]*outDir[0] + outDir[1]*outDir[1] + outDir[2]*outDir[2]);
        if (len > 0.0001f) {
            outDir[0] /= len;
            outDir[1] /= len;
            outDir[2] /= len;
        }
    }

    // 2. Cek sentuhan pada semua objek (Kiri / Tengah / Kanan) & pilih yang terdekat
    public int pickObjectAtTouch(float touchX, float touchY) {
        if (objects == null || objects.isEmpty()) return -1;

        float[] rayOrigin = new float[3];
        float[] rayDir = new float[3];
        screenToWorldRay(touchX, touchY, rayOrigin, rayDir);

        int hitIndex = -1;
        float closestDist = Float.MAX_VALUE;

        for (int i = 0; i < objects.size(); i++) {
            EditorObject obj = objects.get(i);
            // Posisi pusat objek di dunia 3D (sesuaikan dengan nama variabel posisi di EditorObject kamu)
            float cx = obj.offsetX;
            float cy = obj.offsetY;
            float cz = obj.offsetZ;
            float radius = 1.2f * Math.max(obj.scaleX, Math.max(obj.scaleY, obj.scaleZ));

            // Hitung jarak terdekat pusat objek ke garis Ray
            float ocX = cx - rayOrigin[0];
            float ocY = cy - rayOrigin[1];
            float ocZ = cz - rayOrigin[2];

            float tca = ocX * rayDir[0] + ocY * rayDir[1] + ocZ * rayDir[2];
            if (tca < 0) continue; // Objek berada di belakang kamera

            float d2 = (ocX * ocX + ocY * ocY + ocZ * ocZ) - (tca * tca);
            if (d2 <= radius * radius && tca < closestDist) {
                closestDist = tca;
                hitIndex = i;
            }
        }
        return hitIndex;
    }

    // 3. Geser objek aktif mengikuti gerakan jari sejajar pandangan kamera
    public void dragActiveObjectOnScreen(float dxPixel, float dyPixel) {
        EditorObject active = getActiveObject();
        if (active == null) return;

        // Ambil vektor Right & Up kamera dari mViewMatrix
        float rightX = viewMatrix[0];
        float rightY = viewMatrix[4];
        float rightZ = viewMatrix[8];

        float upX = viewMatrix[1];
        float upY = viewMatrix[5];
        float upZ = viewMatrix[9];

        float sensitivity = 0.005f; // Sesuaikan dengan jarak zoom kamera
        float moveX = (dxPixel * rightX - dyPixel * upX) * sensitivity;
        float moveY = (dxPixel * rightY - dyPixel * upY) * sensitivity;
        float moveZ = (dxPixel * rightZ - dyPixel * upZ) * sensitivity;

        active.offsetX += moveX;
        active.offsetY += moveY;
        active.offsetZ += moveZ;
    }
	
	
	public void rotateActiveObjectScreenAligned(float dx, float dy) {
		EditorObject active = getActiveObject();
		if (active == null) return;

		if (active.rotationMatrix[0] == 0f && active.rotationMatrix[5] == 0f && active.rotationMatrix[10] == 0f) {
			Matrix.setIdentityM(active.rotationMatrix, 0);
		}

		float angle = (float) Math.hypot(dx, dy) * 0.5f;
		if (angle < 0.0001f) return;

		float[] screenAxis = new float[]{dy, dx, 0.0f, 0.0f};

		float[] roomRotMat = new float[16];
		Matrix.setIdentityM(roomRotMat, 0);
		Matrix.rotateM(roomRotMat, 0, rotX, 1f, 0f, 0f);
		Matrix.rotateM(roomRotMat, 0, rotY, 0f, 1f, 0f);

		float[] invRoomRotMat = new float[16];
		if (Matrix.invertM(invRoomRotMat, 0, roomRotMat, 0)) {
			float[] worldAxis = new float[4];
			Matrix.multiplyMV(worldAxis, 0, invRoomRotMat, 0, screenAxis, 0);

			float[] deltaRot = new float[16];
			Matrix.setIdentityM(deltaRot, 0);
			Matrix.rotateM(deltaRot, 0, angle, worldAxis[0], worldAxis[1], worldAxis[2]);

			float[] resultMat = new float[16];
			Matrix.multiplyMM(resultMat, 0, deltaRot, 0, active.rotationMatrix, 0);
			System.arraycopy(resultMat, 0, active.rotationMatrix, 0, 16);
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
			activeObj.mesh.vertexColors.clear();
			activeObj.mesh.texCoords.clear();
			activeObj.mesh.normals.clear();
			activeObj.mesh.normalTriangles.clear();
			activeObj.mesh.uvTriangles.clear();
			activeObj.mesh.faceColors.clear();
			activeObj.mesh.lastMtlStatus="No MTL";
			activeObj.modelPath="";
			activeObj.mtlStatus="No MTL";
			
        }

        if (objects.size() == 1) {
			SharedPreferences pref = context.getSharedPreferences("MiniBlenderPrefs", Context.MODE_PRIVATE);
            pref.edit().remove("cached_scene_json").apply();

            File cacheFile = new File(getAppStorageDir(), "miniblender_scene_cache.json");
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
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
		saveAllObjectsToCache(context, activeObjectIndex);
        saveSceneToCache();
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

	public int loadAllObjectsFromCache(Context context) {
		SharedPreferences pref = context.getSharedPreferences("MiniBlenderPrefs", Context.MODE_PRIVATE);
		String jsonString = pref.getString("cached_scene_json", null);
		resetRoomTransform();
		if (jsonString == null || jsonString.isEmpty()) {
			return 0; 
		}

		try {
			objects.clear();
			JSONObject sceneRoot = new JSONObject(jsonString);
			int activeIndex = sceneRoot.optInt("activeObjectIndex", 0);

			JSONArray objectsArray = sceneRoot.getJSONArray("objects");
			for (int i = 0; i < objectsArray.length(); i++) {
				JSONObject objJson = objectsArray.getJSONObject(i);
				EditorObject obj = new EditorObject();
				obj.mesh.initShader(); 
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
		this.screenWidth = width;
		this.screeHeight = height;
        android.opengl.Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 1.0f, 100.0f);
    }
	private java.io.File getAppStorageDir() {
        java.io.File dir = context.getExternalFilesDir(null);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
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
			
			if (obj.pendingTextureBitmap != null && !obj.pendingTextureBitmap.isRecycled()) {
				GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
				int[] textureHandles = new int[1];
				GLES20.glGenTextures(1, textureHandles, 0);

				if (textureHandles[0] != 0) {
					if (obj.textureId > 0) {
						GLES20.glDeleteTextures(1, new int[]{obj.textureId}, 0);
					}
					obj.textureId = textureHandles[0];
					if (context instanceof MainActivity) {
						((MainActivity) context).runOnUiThread(new Runnable() {
								@Override
								public void run() {
									((MainActivity) context).updateOverlayInfo();
								}
							});
					}

					GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, obj.textureId);
					GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
					GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
					GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
					GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

					android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, obj.pendingTextureBitmap, 0);
					GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
				}

				obj.pendingTextureBitmap.recycle();
				obj.pendingTextureBitmap = null;
			}

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
			
			Matrix.setIdentityM(scratchModelMatrix, 0);
			Matrix.translateM(scratchModelMatrix, 0, obj.offsetX, obj.offsetY, obj.offsetZ);

			float[] tempMat = new float[16];
			Matrix.multiplyMM(tempMat, 0, scratchModelMatrix, 0, obj.rotationMatrix, 0);
			System.arraycopy(tempMat, 0, scratchModelMatrix, 0, 16);
			
            System.arraycopy(roomMatrix, 0, scratchModelMatrix, 0, 16);
            android.opengl.Matrix.scaleM(scratchModelMatrix, 0, scaleFactor, scaleFactor, scaleFactor);

            
            android.opengl.Matrix.translateM(scratchModelMatrix, 0, obj.offsetX, obj.offsetY, obj.offsetZ);

            if (obj.rotationMatrix[0] == 0f && obj.rotationMatrix[5] == 0f && obj.rotationMatrix[10] == 0f) {
                android.opengl.Matrix.setIdentityM(obj.rotationMatrix, 0);
            }
            android.opengl.Matrix.multiplyMM(tempMatrix, 0, scratchModelMatrix, 0, obj.rotationMatrix, 0);
            System.arraycopy(tempMatrix, 0, scratchModelMatrix, 0, 16);

            
            android.opengl.Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, scratchModelMatrix, 0);
            android.opengl.Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0);
			android.opengl.Matrix.scaleM(scratchModelMatrix, 0, obj.scaleX, obj.scaleY, obj.scaleZ);
			
			
            obj.mesh.draw(mvpMatrix, scratchModelMatrix, obj.textureId, obj.selectedVertexIndex, isEditMode && isThisObjectActive);
			
        }
    }

	public void loadTextureForActiveObject(final Bitmap srcBitmap, final String texturePath) {
        EditorObject active = getActiveObject();
        if (active == null || srcBitmap == null || srcBitmap.isRecycled()) return;

        int w = srcBitmap.getWidth();
        int h = srcBitmap.getHeight();
        Bitmap flippedBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(flippedBitmap);
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postScale(1.0f, -1.0f, w * 0.5f, h * 0.5f);
        canvas.drawBitmap(srcBitmap, matrix, null);

        if (!srcBitmap.isRecycled()) {
            srcBitmap.recycle();
        }

        if (active.pendingTextureBitmap != null && !active.pendingTextureBitmap.isRecycled()) {
            active.pendingTextureBitmap.recycle();
        }
        active.pendingTextureBitmap = flippedBitmap;
        if (texturePath != null) {
            active.texturePath = texturePath;
        }
		java.io.File cacheTexFile = new java.io.File(getAppStorageDir(), "tex_" + active.name.replaceAll("[^a-zA-Z0-9._-]", "_") + ".png");
		try {
            if (active.texturePath != null && active.texturePath.contains("tex_")) {
                java.io.File oldTex = new java.io.File(active.texturePath);
                if (oldTex.exists()) oldTex.delete();
            }

            java.io.FileOutputStream fos = new java.io.FileOutputStream(cacheTexFile);
            flippedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            active.texturePath = cacheTexFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            if (texturePath != null) {
                active.texturePath = texturePath;
            }
        }

        /*if (active.pendingTextureBitmap != null && !active.pendingTextureBitmap.isRecycled()) {
            active.pendingTextureBitmap.recycle();
        }
        active.pendingTextureBitmap = flippedBitmap;
		*/
        saveSceneToCache();
		
		
    }
	public void clearAllOldCache() {
        
        android.content.SharedPreferences prefs = context.getSharedPreferences("MiniBlenderPrefs", android.content.Context.MODE_PRIVATE);
        prefs.edit().clear().apply();

        deleteDirContents(context.getCacheDir());
        deleteDirContents(context.getFilesDir());

        deleteDirContents(context.getExternalCacheDir());
        deleteDirContents(getAppStorageDir());
    }

    private void deleteDirContents(java.io.File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    if (f.isDirectory()) {
                        deleteDirContents(f);
                    }
                    f.delete();
                }
            }
        }
    }
	
	

    public void saveSceneToCache() {
        try {
            File cacheFile = new File(getAppStorageDir(), "miniblender_scene_cache.json");
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
			android.content.SharedPreferences prefs = context.getSharedPreferences("MiniBlenderPrefs", android.content.Context.MODE_PRIVATE);
			prefs.edit()
				.putInt("schema_version", 2) 
				.putString("scene_json_path", cacheFile.getAbsolutePath())
				.putInt("active_index", activeObjectIndex)
				.apply();
			
        } catch (Exception ignored) {}
    }

	public boolean restoreSceneFromCache() {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("MiniBlenderPrefs", android.content.Context.MODE_PRIVATE);
            int savedVersion = prefs.getInt("schema_version", 1);
            if (savedVersion != 2) {
                clearAllOldCache();
                return false;
            }

            File cacheFile = new File(getAppStorageDir(), "miniblender_scene_cache.json");
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

                // Langsung pasang ke obj[i] masing-masing tanpa memanggil loadTextureForActiveObject!
                if (!obj.texturePath.isEmpty()) {
                    File texFile = new File(obj.texturePath);
                    if (texFile.exists()) {
                        Bitmap bmp = BitmapFactory.decodeFile(texFile.getAbsolutePath());
                        if (bmp != null) {
                            obj.pendingTextureBitmap = bmp;
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

