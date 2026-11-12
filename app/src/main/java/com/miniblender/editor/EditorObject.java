package com.miniblender.editor;

import org.json.JSONArray;
import org.json.JSONObject;

public class EditorObject {
    public String name;
    public MeshModel mesh;
    public float offsetX = 0.0f;
    public float offsetY = 0.0f;
    public float offsetZ = 0.0f;
    public float rotX = 0.0f;
    public float rotY = 0.0f;
    public float rotZ = 0.0f;
    public int selectedVertexIndex = 0;
    public int textureId = 0;
    public String texturePath = ""; 
	public float[] rotationMatrix = new float[16];
	public String modelPath = "";
	public String mtlStatus = "No MTL";
	public transient android.graphics.Bitmap pendingTextureBitmap = null;
	

    public EditorObject(String name) {
        this.name = name;
        this.mesh = new MeshModel();
    }

    // Constructor kosong untuk penciptaan dinamis saat load JSON
    public EditorObject() {
        this.name = "Object";
        this.mesh = new MeshModel();
    }
	public void applyMTLFromStream(java.io.InputStream is, String mtlName) throws Exception {
		if (is == null || mesh == null) return;

		java.util.Map<String, float[]> parsed = new java.util.HashMap<>();
		java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
		String line;
		String currentMtl = null;

		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) continue;

			int commentIdx = line.indexOf('#');
			if (commentIdx != -1) line = line.substring(0, commentIdx).trim();
			if (line.isEmpty()) continue;

			String[] tokens = line.split("\\s+");
			if (tokens.length == 0) continue;

			if (tokens[0].equalsIgnoreCase("newmtl") && tokens.length >= 2) {
				currentMtl = line.substring(line.indexOf(' ') + 1).trim();
			} else if (tokens[0].equalsIgnoreCase("Kd") && tokens.length >= 4 && currentMtl != null) {
				try {
					float r = Float.parseFloat(tokens[1]);
					float g = Float.parseFloat(tokens[2]);
					float b = Float.parseFloat(tokens[3]);
					parsed.put(currentMtl, new float[]{r, g, b});
				} catch (NumberFormatException ignored) {}
			}
		}
		reader.close();

		if (parsed.isEmpty()) {
			this.mtlStatus = "MTL Kosong / Tidak ada Kd: " + mtlName;
			return;
		}

		mesh.faceColors.clear();
		int matched = 0;
		for (int i = 0; i < mesh.triangles.size(); i++) {
			String mat = (i < mesh.faceMaterialNames.size()) ? mesh.faceMaterialNames.get(i) : "";
			if (parsed.containsKey(mat)) {
				mesh.faceColors.add(parsed.get(mat));
				matched++;
			} else {
				mesh.faceColors.add(new float[]{0.75f, 0.75f, 0.8f});
			}
		}

		mesh.hasMtlColors = (matched > 0);
		mesh.hasVertexColors = (matched > 0);
		this.mtlStatus = "MTL Loaded (" + parsed.size() + " mat, " + matched + " faces)";
		mesh.updateBuffers();
	}
	

    // ==========================================
    // 1. FUNGSI SIMPAN OBJECT KE JSONOBJECT
    // ==========================================
    public JSONObject toJSONObject() throws Exception {
        JSONObject root = new JSONObject();

        // A. Simpan Atribut & Transformasi EditorObject
        root.put("name", name);
        root.put("offsetX", (double) offsetX);
        root.put("offsetY", (double) offsetY);
        root.put("offsetZ", (double) offsetZ);
        root.put("rotX", (double) rotX);
        root.put("rotY", (double) rotY);
        root.put("rotZ", (double) rotZ);
        root.put("selectedVertexIndex", selectedVertexIndex);
        root.put("texturePath", texturePath);
		root.put("modelPath", modelPath != null ? modelPath : "");
		root.put("mtlStatus", mtlStatus != null ? mtlStatus : "No MTL");
		
		root.put("hasMtlColors", mesh.hasMtlColors);

		org.json.JSONArray fcArray = new org.json.JSONArray();
		for (float[] fc : mesh.faceColors) {
			org.json.JSONArray rgb = new org.json.JSONArray();
			rgb.put(fc[0]);
			rgb.put(fc[1]);
			rgb.put(fc[2]);
			fcArray.put(rgb);
		}
		root.put("faceColors", fcArray);
		
		
		JSONArray rotMatArray = new JSONArray();
		for (int i = 0; i < 16; i++) {
			rotMatArray.put(rotationMatrix[i]);
		}
		// B. Simpan Matrix
		root.put("rotationMatrix", rotMatArray);

        // C. Simpan Mesh Vertices (v)
        JSONArray vArray = new JSONArray();
        for (float[] v : mesh.vertices) {
            JSONArray item = new JSONArray();
            item.put((double) v[0]);
            item.put((double) v[1]);
            item.put((double) v[2]);
            vArray.put(item);
        }
        root.put("vertices", vArray);

        // D. Simpan UV Texture Coordinates (vt)
        JSONArray vtArray = new JSONArray();
        for (float[] vt : mesh.texCoords) {
            JSONArray item = new JSONArray();
            item.put((double) vt[0]);
            item.put((double) vt[1]);
            vtArray.put(item);
        }
        root.put("texCoords", vtArray);

        // E. Simpan Normals (vn)
        JSONArray vnArray = new JSONArray();
        for (float[] vn : mesh.normals) {
            JSONArray item = new JSONArray();
            item.put((double) vn[0]);
            item.put((double) vn[1]);
            item.put((double) vn[2]);
            vnArray.put(item);
        }
        root.put("normals", vnArray);

        // F. Simpan Triangles (f - vertex indices)
        JSONArray fArray = new JSONArray();
        for (int[] tri : mesh.triangles) {
            JSONArray item = new JSONArray();
            item.put(tri[0]);
            item.put(tri[1]);
            item.put(tri[2]);
            fArray.put(item);
        }
        root.put("triangles", fArray);

        // G. Simpan UV Triangles
        JSONArray uvFArray = new JSONArray();
        for (int[] uvTri : mesh.uvTriangles) {
            JSONArray item = new JSONArray();
            item.put(uvTri[0]);
            item.put(uvTri[1]);
            item.put(uvTri[2]);
            uvFArray.put(item);
        }
        root.put("uvTriangles", uvFArray);

        // H. Simpan Normal Triangles
        JSONArray normFArray = new JSONArray();
        for (int[] normTri : mesh.normalTriangles) {
            JSONArray item = new JSONArray();
            item.put(normTri[0]);
            item.put(normTri[1]);
            item.put(normTri[2]);
            normFArray.put(item);
        }
        root.put("normalTriangles", normFArray);

        // I. Simpan Status Winding Mode Aktif
        root.put("isCCWMode", mesh.isCCWMode);

        return root;
    }

    // ==========================================
    // 2. FUNGSI MUAT OBJECT DARI JSONOBJECT
    // ==========================================
    public void fromJSONObject(JSONObject root) throws Exception {
        mesh.vertices.clear();
        mesh.texCoords.clear();
        mesh.normals.clear();
        mesh.triangles.clear();
        mesh.uvTriangles.clear();
        mesh.normalTriangles.clear();

        // A. Restore Atribut & Transformasi EditorObject
        this.name = root.optString("name", "Object");
        this.offsetX = (float) root.optDouble("offsetX", 0.0);
        this.offsetY = (float) root.optDouble("offsetY", 0.0);
        this.offsetZ = (float) root.optDouble("offsetZ", 0.0);
        this.rotX = (float) root.optDouble("rotX", 0.0);
        this.rotY = (float) root.optDouble("rotY", 0.0);
        this.rotZ = (float) root.optDouble("rotZ", 0.0);
        this.selectedVertexIndex = root.optInt("selectedVertexIndex", 0);
        this.texturePath = root.optString("texturePath", "");
		this.modelPath = root.optString("modelPath", "");
		this.mtlStatus = root.optString("mtlStatus", "No MTL");
		mesh.hasMtlColors = root.optBoolean("hasMtlColors", false);
		mesh.faceColors.clear();
		org.json.JSONArray fcArray = root.optJSONArray("faceColors");
		if (fcArray != null) {
			for (int i = 0; i < fcArray.length(); i++) {
				org.json.JSONArray rgb = fcArray.getJSONArray(i);
				mesh.faceColors.add(new float[]{
										(float) rgb.getDouble(0),
										(float) rgb.getDouble(1),
										(float) rgb.getDouble(2)
									});
			}
		}
		if (mesh.hasMtlColors && !mesh.faceColors.isEmpty()) {
			mesh.hasVertexColors = true;
		}

        // B. Baca Vertices
        if (root.has("vertices")) {
            JSONArray vArray = root.getJSONArray("vertices");
            for (int i = 0; i < vArray.length(); i++) {
                JSONArray item = vArray.getJSONArray(i);
                mesh.vertices.add(new float[]{
									  (float) item.getDouble(0),
									  (float) item.getDouble(1),
									  (float) item.getDouble(2)
								  });
            }
        }

        // C. Baca UV Coordinates
        if (root.has("texCoords")) {
            JSONArray vtArray = root.getJSONArray("texCoords");
            for (int i = 0; i < vtArray.length(); i++) {
                JSONArray item = vtArray.getJSONArray(i);
                mesh.texCoords.add(new float[]{
									   (float) item.getDouble(0),
									   (float) item.getDouble(1)
								   });
            }
        }

        // D. Baca Normals (vn)
        if (root.has("normals")) {
            JSONArray vnArray = root.getJSONArray("normals");
            for (int i = 0; i < vnArray.length(); i++) {
                JSONArray item = vnArray.getJSONArray(i);
                mesh.normals.add(new float[]{
									 (float) item.getDouble(0),
									 (float) item.getDouble(1),
									 (float) item.getDouble(2)
								 });
            }
        }

        // E. Baca Vertex Triangles
        if (root.has("triangles")) {
            JSONArray fArray = root.getJSONArray("triangles");
            for (int i = 0; i < fArray.length(); i++) {
                JSONArray item = fArray.getJSONArray(i);
                mesh.triangles.add(new int[]{
									   item.getInt(0),
									   item.getInt(1),
									   item.getInt(2)
								   });
            }
        }

        // F. Baca UV Triangles
        if (root.has("uvTriangles")) {
            JSONArray uvFArray = root.getJSONArray("uvTriangles");
            for (int i = 0; i < uvFArray.length(); i++) {
                JSONArray item = uvFArray.getJSONArray(i);
                mesh.uvTriangles.add(new int[]{
										 item.getInt(0),
										 item.getInt(1),
										 item.getInt(2)
									 });
            }
        }

        // G. Baca Normal Triangles
        if (root.has("normalTriangles")) {
            JSONArray normFArray = root.getJSONArray("normalTriangles");
            for (int i = 0; i < normFArray.length(); i++) {
                JSONArray item = normFArray.getJSONArray(i);
                mesh.normalTriangles.add(new int[]{
											 item.getInt(0),
											 item.getInt(1),
											 item.getInt(2)
										 });
            }
        }
		if (root.has("rotationMatrix")){
			JSONArray rotMatArray = root.optJSONArray("rotationMatrix");
			if (rotMatArray != null && rotMatArray.length() == 16) {
				for (int i = 0; i < 16; i++) {
					this.rotationMatrix[i] = (float) rotMatArray.getDouble(i);
				}
			} else {
				android.opengl.Matrix.setIdentityM(this.rotationMatrix, 0);
			}
		}

        // H. Restore Winding Mode
        if (root.has("isCCWMode")) {
            mesh.isCCWMode = root.getBoolean("isCCWMode");
        }

        if (mesh.texCoords.isEmpty()) {
            for (int i = 0; i < mesh.vertices.size(); i++) mesh.texCoords.add(new float[]{0.5f, 0.5f});
        }
        if (mesh.normals.isEmpty()) {
            mesh.recalculateNormals();
        }

        mesh.updateBuffers();
    }
}

