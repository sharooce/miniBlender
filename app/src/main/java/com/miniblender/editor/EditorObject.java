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
	public float scaleX = 1.0f;
    public float scaleY = 1.0f;
    public float scaleZ = 1.0f;
    public int selectedVertexIndex = 0;
	public float boundingRadius = 1.2f;
    public int textureId = 0;
    public String texturePath = ""; 
	public float[] rotationMatrix = new float[16];
	public String modelPath = "";
	public String mtlStatus = "No MTL";
	public transient android.graphics.Bitmap pendingTextureBitmap = null;
	public transient MeshHistoryManager history = new MeshHistoryManager();
	
	public void resetView(){
		this.offsetX = 0.0f;
		this.offsetY = 0.0f;
		this.offsetZ = 0.0f;
		this.rotX = 0.0f;
		this.rotY = 0.0f;
		this.rotZ = 0.0f;
		this.scaleX = 1.0f;
		this.scaleY = 1.0f;
		this.scaleZ = 1.0f;
	}
	
	
    public EditorObject(String name) {
        this.name = name;
        this.mesh = new MeshModel();
    }

   
    public EditorObject() {
        this.name = "Object";
        this.mesh = new MeshModel();
    }
	public void updateBoundingRadius() {
        if (mesh == null || mesh.vertices == null || mesh.vertices.isEmpty()) {
            boundingRadius = 1.0f;
            return;
        }
        float maxDistSq = 0.25f; 
        for (float[] v : mesh.vertices) {
            float d2 = v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
            if (d2 > maxDistSq) {
                maxDistSq = d2;
            }
        }
        boundingRadius = (float) Math.sqrt(maxDistSq);
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
	

    public JSONObject toJSONObject() throws Exception {
        JSONObject root = new JSONObject();

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
		root.put("boundingRadius", (double) this.boundingRadius);
		root.put("scaleX", (double) this.scaleX);
		root.put("scaleY", (double) this.scaleY);
		root.put("scaleZ", (double) this.scaleZ);
		
		
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
		
		root.put("rotationMatrix", rotMatArray);

        JSONArray vArray = new JSONArray();
        for (float[] v : mesh.vertices) {
            JSONArray item = new JSONArray();
            item.put((double) v[0]);
            item.put((double) v[1]);
            item.put((double) v[2]);
            vArray.put(item);
        }
        root.put("vertices", vArray);

        JSONArray vtArray = new JSONArray();
        for (float[] vt : mesh.texCoords) {
            JSONArray item = new JSONArray();
            item.put((double) vt[0]);
            item.put((double) vt[1]);
            vtArray.put(item);
        }
        root.put("texCoords", vtArray);

        JSONArray vnArray = new JSONArray();
        for (float[] vn : mesh.normals) {
            JSONArray item = new JSONArray();
            item.put((double) vn[0]);
            item.put((double) vn[1]);
            item.put((double) vn[2]);
            vnArray.put(item);
        }
        root.put("normals", vnArray);

        JSONArray fArray = new JSONArray();
        for (int[] tri : mesh.triangles) {
            JSONArray item = new JSONArray();
            item.put(tri[0]);
            item.put(tri[1]);
            item.put(tri[2]);
            fArray.put(item);
        }
        root.put("triangles", fArray);

        JSONArray uvFArray = new JSONArray();
        for (int[] uvTri : mesh.uvTriangles) {
            JSONArray item = new JSONArray();
            item.put(uvTri[0]);
            item.put(uvTri[1]);
            item.put(uvTri[2]);
            uvFArray.put(item);
        }
        root.put("uvTriangles", uvFArray);

        JSONArray normFArray = new JSONArray();
        for (int[] normTri : mesh.normalTriangles) {
            JSONArray item = new JSONArray();
            item.put(normTri[0]);
            item.put(normTri[1]);
            item.put(normTri[2]);
            normFArray.put(item);
        }
        root.put("normalTriangles", normFArray);
        root.put("isCCWMode", mesh.isCCWMode);

        return root;
    }

    public void fromJSONObject(JSONObject root) throws Exception {
        mesh.vertices.clear();
        mesh.texCoords.clear();
        mesh.normals.clear();
        mesh.triangles.clear();
        mesh.uvTriangles.clear();
        mesh.normalTriangles.clear();

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
		this.boundingRadius = (float) root.optDouble("boundingRadius", 1.2);
		this.scaleX = (float) root.optDouble("scaleX", 1.0);
	    this.scaleY = (float) root.optDouble("scaleY", 1.0);
		this.scaleZ = (float) root.optDouble("scaleZ", 1.0);
		
		
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

        if (root.has("isCCWMode")) {
            mesh.isCCWMode = root.getBoolean("isCCWMode");
        }

        if (mesh.texCoords.isEmpty()) {
            for (int i = 0; i < mesh.vertices.size(); i++) mesh.texCoords.add(new float[]{0.5f, 0.5f});
        }
        if (mesh.normals.isEmpty()) {
            mesh.recalculateNormals();
        }
		if(root.get("texturePath")!=""){
			texturePath=root.getString("texturePath");
		}

        mesh.updateBuffers();
    }
}

