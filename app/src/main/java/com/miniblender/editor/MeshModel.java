package com.miniblender.editor;

import android.opengl.GLES20;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MeshModel {
    public List<float[]> vertices = new ArrayList<>();
    public List<float[]> texCoords = new ArrayList<>();
    public List<float[]> normals = new ArrayList<>();
    public List<int[]> triangles = new ArrayList<>();
    public List<int[]> uvTriangles = new ArrayList<>();
    public List<int[]> normalTriangles = new ArrayList<>();
    public List<float[]> vertexColors = new ArrayList<>();
    public List<float[]> faceColors = new ArrayList<>();
	public List<String> faceMaterialNames = new ArrayList<>();

    private FloatBuffer vertexBuffer;
    private FloatBuffer uvBuffer;
    private FloatBuffer normalBuffer;
    private FloatBuffer colorBuffer;
	private FloatBuffer lineBuffer;
	private int lineVertexCount = 0;
	

    private int vertexColorHandle;
    private int uHasVertexColorHandle;
    private int program;
    private int positionHandle;
    private int normalHandle;
    private int colorHandle;
    private int textureCoordHandle;
    private int mvpMatrixHandle;
    private int modelMatrixHandle;
    private int uTextureHandle;
    private int uUseTextureHandle;
    private int uLightDirHandle;

    private int pointProgram;
    private int pPositionHandle;
    private int pMVPMatrixHandle;
    private int pColorHandle;
    private int pPointSizeHandle;
	private int uRgbWireframeHandle;
	

    public boolean isCCWMode = true;
    public boolean hasVertexColors = false;
    public boolean hasMtlColors = false;
	
	public String lastMtlStatus = "No MTL";

	private final String vertexShaderCode =
	"uniform mat4 uMVPMatrix;\n" +
	"uniform mat4 uModelMatrix;\n" +
	"attribute vec4 aPosition;\n" +
	"attribute vec2 aTexCoord;\n" +
	"attribute vec3 aNormal;\n" +
	"attribute vec3 aVertexColor;\n" +
	"varying vec2 vTexCoord;\n" +
	"varying vec3 vNormal;\n" +
	"varying vec3 vVertexColor;\n" +
	"varying vec3 vLocalPos;\n" + 
	"void main() {\n" +
	"  gl_Position = uMVPMatrix * aPosition;\n" +
	"  vTexCoord = aTexCoord;\n" +
	"  vNormal = normalize(mat3(uMVPMatrix) * aNormal);\n" +
	"  vVertexColor = aVertexColor;\n" +
	"  vLocalPos = aPosition.xyz;\n" +
	"}\n";

    private final String fragmentShaderCode =
	"precision mediump float;\n" +
	"varying vec2 vTexCoord;\n" +
	"varying vec3 vNormal;\n" +
	"varying vec3 vVertexColor;\n" +
	"varying vec3 vLocalPos;\n" +
	"uniform sampler2D uTexture;\n" +
	"uniform int uUseTexture;\n" +
	"uniform int uHasVertexColor;\n" +
	"uniform int uRgbWireframe;\n" + 
	"uniform vec4 uColor;\n" +
	"uniform vec3 uLightDir;\n" +
	"void main() {\n" +
	"  if (uRgbWireframe == 1) {\n" +
	"    // Gradasi warna RGB cerah berdasarkan koordinat XYZ model\n" +
	"    vec3 rgb = 0.5 + 0.5 * sin(vLocalPos * 3.0 + vec3(0.0, 2.094, 4.188));\n" +
	"    gl_FragColor = vec4(rgb, 1.0);\n" +
	"    return;\n" +
	"  }\n" +
	"  \n" +
	"  vec3 norm = normalize(vNormal);\n" +
	"  vec3 lightDir = normalize(uLightDir);\n" +
	"  float diff = max(dot(norm, lightDir), 0.0);\n" +
	"  float intensity = 0.75 + (0.25 * diff);\n" +
	"  \n" +
	"  if (uUseTexture == 1) {\n" +
	"    vec4 texColor = texture2D(uTexture, vTexCoord);\n" +
	"    vec3 baseRgb = (uHasVertexColor == 1) ? (texColor.rgb * vVertexColor) : texColor.rgb;\n" +
	"    gl_FragColor = vec4(baseRgb * intensity, texColor.a);\n" +
	"  } else if (uHasVertexColor == 1) {\n" +
	"    gl_FragColor = vec4(vVertexColor * intensity, 1.0);\n" +
	"  } else {\n" +
	"    gl_FragColor = vec4(uColor.rgb * intensity, uColor.a);\n" +
	"  }\n" +
	"}\n";
	
	

    private final String pointVertexShaderCode =
	"uniform mat4 uMVPMatrix;\n" +
	"uniform float uPointSize;\n" +
	"attribute vec4 aPosition;\n" +
	"void main() {\n" +
	"  gl_Position = uMVPMatrix * aPosition;\n" +
	"  gl_PointSize = uPointSize;\n" +
	"}\n";

    private final String pointFragmentShaderCode =
	"precision mediump float;\n" +
	"uniform vec4 uColor;\n" +
	"void main() {\n" +
	"  gl_FragColor = uColor;\n" +
	"}\n";

    public MeshModel() {}

    public void addVertex(float x, float y, float z, float u, float v) {
        vertices.add(new float[]{x, y, z});
        texCoords.add(new float[]{u, v});
        normals.add(new float[]{0.0f, 1.0f, 0.0f});
        vertexColors.add(new float[]{1.0f, 1.0f, 1.0f});
    }

    public int addNewVertex(float x, float y, float z) {
        vertices.add(new float[]{x, y, z});
        texCoords.add(new float[]{0.5f, 0.5f});
        normals.add(new float[]{0.0f, 1.0f, 0.0f});
        vertexColors.add(new float[]{1.0f, 1.0f, 1.0f});
        updateBuffers();
        return vertices.size() - 1;
    }

    public int extrudeFromSelectedVertex(int selectedIndex, float offsetX, float offsetY, float offsetZ) {
        if (selectedIndex < 0 || selectedIndex >= vertices.size()) return selectedIndex;
        float[] base = vertices.get(selectedIndex);
        float[] uvBase = (selectedIndex < texCoords.size()) ? texCoords.get(selectedIndex) : new float[]{0.5f, 0.5f};

        float newX = base[0] + offsetX;
        float newY = base[1] + offsetY;
        float newZ = base[2] + offsetZ;

        vertices.add(new float[]{newX, newY, newZ});
        texCoords.add(new float[]{uvBase[0], uvBase[1]});
        normals.add(new float[]{0.0f, 1.0f, 0.0f});
        vertexColors.add(new float[]{1.0f, 1.0f, 1.0f});

        int newIndex = vertices.size() - 1;
        triangles.add(new int[]{selectedIndex, newIndex, (selectedIndex + 1) % vertices.size()});
        uvTriangles.add(new int[]{0, 0, 0});
        normalTriangles.add(new int[]{0, 0, 0});
        updateBuffers();
        return newIndex;
    }

    public void initShader() {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal");
        textureCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord");
        colorHandle = GLES20.glGetUniformLocation(program, "uColor");
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        modelMatrixHandle = GLES20.glGetUniformLocation(program, "uModelMatrix");
        uTextureHandle = GLES20.glGetUniformLocation(program, "uTexture");
        uUseTextureHandle = GLES20.glGetUniformLocation(program, "uUseTexture");
        uLightDirHandle = GLES20.glGetUniformLocation(program, "uLightDir");
        vertexColorHandle = GLES20.glGetAttribLocation(program, "aVertexColor");
        uHasVertexColorHandle = GLES20.glGetUniformLocation(program, "uHasVertexColor");
		uRgbWireframeHandle = GLES20.glGetUniformLocation(program, "uRgbWireframe");
		

        int pVertexShader = loadShader(GLES20.GL_VERTEX_SHADER, pointVertexShaderCode);
        int pFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, pointFragmentShaderCode);

        pointProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(pointProgram, pVertexShader);
        GLES20.glAttachShader(pointProgram, pFragmentShader);
        GLES20.glLinkProgram(pointProgram);

        pPositionHandle = GLES20.glGetAttribLocation(pointProgram, "aPosition");
        pMVPMatrixHandle = GLES20.glGetUniformLocation(pointProgram, "uMVPMatrix");
        pColorHandle = GLES20.glGetUniformLocation(pointProgram, "uColor");
        pPointSizeHandle = GLES20.glGetUniformLocation(pointProgram, "uPointSize");
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    public void recalculateNormals() {
        for (float[] n : normals) {
            n[0] = 0.0f; n[1] = 0.0f; n[2] = 0.0f;
        }

        for (int[] tri : triangles) {
            if (tri[0] >= vertices.size() || tri[1] >= vertices.size() || tri[2] >= vertices.size()) continue;

            float[] v0 = vertices.get(tri[0]);
            float[] v1 = vertices.get(tri[1]);
            float[] v2 = vertices.get(tri[2]);

            float ax = v1[0] - v0[0], ay = v1[1] - v0[1], az = v1[2] - v0[2];
            float bx = v2[0] - v0[0], by = v2[1] - v0[1], bz = v2[2] - v0[2];

            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;

            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 0.0001f) {
                nx /= len; ny /= len; nz /= len;
            }

            for (int idx : tri) {
                if (idx >= 0 && idx < normals.size()) {
                    float[] n = normals.get(idx);
                    n[0] += nx; n[1] += ny; n[2] += nz;
                }
            }
        }

        for (float[] n : normals) {
            float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            if (len > 0.0001f) {
                n[0] /= len; n[1] /= len; n[2] /= len;
            } else {
                n[0] = 0.0f; n[1] = 1.0f; n[2] = 0.0f;
            }
        }
    }

    public void updateBuffers() {
        if (triangles.isEmpty()) return;

        List<Float> finalVerts = new ArrayList<>();
        List<Float> finalUvs = new ArrayList<>();
        List<Float> finalNorms = new ArrayList<>();
        List<Float> finalColors = new ArrayList<>();

        for (int i = 0; i < triangles.size(); i++) {
            int[] tri = triangles.get(i);
            int[] uvTri = (i < uvTriangles.size() && uvTriangles.get(i) != null) ? uvTriangles.get(i) : tri;
            int[] normTri = (i < normalTriangles.size() && normalTriangles.get(i) != null) ? normalTriangles.get(i) : tri;

            float[] triMtlColor = (hasMtlColors && i < faceColors.size()) ? faceColors.get(i) : null;

            for (int j = 0; j < 3; j++) {
                // 1. Vertex Position
                int vIdx = tri[j];
                if (vIdx >= 0 && vIdx < vertices.size()) {
                    float[] v = vertices.get(vIdx);
                    finalVerts.add(v[0]);
                    finalVerts.add(v[1]);
                    finalVerts.add(v[2]);
                } else {
                    finalVerts.add(0f); finalVerts.add(0f); finalVerts.add(0f);
                }

                // 2. Texture Coordinates (UV)
                int uvIdx = uvTri[j];
                if (uvIdx >= 0 && uvIdx < texCoords.size()) {
                    float[] uv = texCoords.get(uvIdx);
                    finalUvs.add(uv[0]);
                    finalUvs.add(uv[1]);
                } else {
                    finalUvs.add(0f);
                    finalUvs.add(0f);
                }

                // 3. Normals
                int nIdx = normTri[j];
                if (nIdx >= 0 && nIdx < normals.size()) {
                    float[] n = normals.get(nIdx);
                    finalNorms.add(n[0]);
                    finalNorms.add(n[1]);
                    finalNorms.add(n[2]);
                } else {
                    finalNorms.add(0f); finalNorms.add(1f); finalNorms.add(0f);
                }

                // 4. Colors (Wajib 1 warna per sudut segitiga -> 3x per triangle)
                if (triMtlColor != null) {
                    finalColors.add(triMtlColor[0]);
                    finalColors.add(triMtlColor[1]);
                    finalColors.add(triMtlColor[2]);
                } else if (vIdx >= 0 && vIdx < vertexColors.size()) {
                    float[] c = vertexColors.get(vIdx);
                    finalColors.add(c[0]);
                    finalColors.add(c[1]);
                    finalColors.add(c[2]);
                } else {
                    finalColors.add(1.0f); finalColors.add(1.0f); finalColors.add(1.0f);
                }
            }
        }
		
		List<Float> finalLines = new ArrayList<>(triangles.size() * 18);
		for (int[] tri : triangles) {
			if (tri[0] >= vertices.size() || tri[1] >= vertices.size() || tri[2] >= vertices.size()) continue;
			float[] v0 = vertices.get(tri[0]);
			float[] v1 = vertices.get(tri[1]);
			float[] v2 = vertices.get(tri[2]);

			// Garis v0 -> v1
			finalLines.add(v0[0]); finalLines.add(v0[1]); finalLines.add(v0[2]);
			finalLines.add(v1[0]); finalLines.add(v1[1]); finalLines.add(v1[2]);
			// Garis v1 -> v2
			finalLines.add(v1[0]); finalLines.add(v1[1]); finalLines.add(v1[2]);
			finalLines.add(v2[0]); finalLines.add(v2[1]); finalLines.add(v2[2]);
			// Garis v2 -> v0
			finalLines.add(v2[0]); finalLines.add(v2[1]); finalLines.add(v2[2]);
			finalLines.add(v0[0]); finalLines.add(v0[1]); finalLines.add(v0[2]);
		}

		lineVertexCount = finalLines.size() / 3;
		lineBuffer = ByteBuffer.allocateDirect(finalLines.size() * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		for (float f : finalLines) lineBuffer.put(f);
		lineBuffer.position(0);

        vertexBuffer = ByteBuffer.allocateDirect(finalVerts.size() * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (float f : finalVerts) vertexBuffer.put(f);
        vertexBuffer.position(0);

        uvBuffer = ByteBuffer.allocateDirect(finalUvs.size() * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (float f : finalUvs) uvBuffer.put(f);
        uvBuffer.position(0);

        normalBuffer = ByteBuffer.allocateDirect(finalNorms.size() * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (float f : finalNorms) normalBuffer.put(f);
        normalBuffer.position(0);

        colorBuffer = ByteBuffer.allocateDirect(finalColors.size() * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (float f : finalColors) colorBuffer.put(f);
        colorBuffer.position(0);
		
		
    }

    public void draw(float[] mvpMatrix, float[] modelMatrix, int textureId, int selectedVertexIndex, boolean isEditMode) {
        if (vertexBuffer == null || uvBuffer == null || normalBuffer == null) return;

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDepthMask(true);

        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0);

        if (uLightDirHandle != -1) {
            GLES20.glUniform3f(uLightDirHandle, 0.0f, 1.0f, 1.0f);
        }
		
		if (uRgbWireframeHandle != -1) {
            GLES20.glUniform1i(uRgbWireframeHandle, 0);
        }

        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        GLES20.glEnableVertexAttribArray(normalHandle);
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer);

        GLES20.glEnableVertexAttribArray(textureCoordHandle);
        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, uvBuffer);

        boolean validColorBuf = (hasVertexColors && colorBuffer != null && colorBuffer.capacity() == vertexBuffer.capacity() && vertexColorHandle != -1);
        if (validColorBuf) {
            GLES20.glEnableVertexAttribArray(vertexColorHandle);
            GLES20.glVertexAttribPointer(vertexColorHandle, 3, GLES20.GL_FLOAT, false, 0, colorBuffer);
        }

        if (textureId > 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glUniform1i(uTextureHandle, 0);
            GLES20.glUniform1i(uUseTextureHandle, 1);
            if (uHasVertexColorHandle != -1) {
                GLES20.glUniform1i(uHasVertexColorHandle, validColorBuf ? 1 : 0);
            }
        } else {
            GLES20.glUniform1i(uUseTextureHandle, 0);
            if (validColorBuf) {
                if (uHasVertexColorHandle != -1) GLES20.glUniform1i(uHasVertexColorHandle, 1);
            } else {
                if (uHasVertexColorHandle != -1) GLES20.glUniform1i(uHasVertexColorHandle, 0);
                GLES20.glUniform4f(colorHandle, 0.75f, 0.75f, 0.8f, 1.0f);
            }
        }

        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL);
        GLES20.glPolygonOffset(1.0f, 1.0f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, triangles.size() * 3);
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);

		if (isEditMode && lineBuffer != null && lineVertexCount > 0) {
            GLES20.glUniform1i(uUseTextureHandle, 0);
            if (uHasVertexColorHandle != -1) GLES20.glUniform1i(uHasVertexColorHandle, 0);
            GLES20.glUniform4f(colorHandle, 1.0f, 0.55f, 0.0f, 1.0f);
			if (uRgbWireframeHandle != -1) GLES20.glUniform1i(uRgbWireframeHandle, 1);

            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, lineBuffer);
            GLES20.glLineWidth(1.5f);
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, lineVertexCount);
			if (uRgbWireframeHandle != -1) GLES20.glUniform1i(uRgbWireframeHandle, 0);
        }
		

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(normalHandle);
        GLES20.glDisableVertexAttribArray(textureCoordHandle);
        if (vertexColorHandle != -1) {
            GLES20.glDisableVertexAttribArray(vertexColorHandle);
        }

		if (isEditMode && pointProgram != 0) {
            if (selectedVertexIndex >= 0 && selectedVertexIndex < vertices.size()) {
                GLES20.glUseProgram(pointProgram);
                GLES20.glUniformMatrix4fv(pMVPMatrixHandle, 1, false, mvpMatrix, 0);
                GLES20.glEnableVertexAttribArray(pPositionHandle);

                float[] selV = vertices.get(selectedVertexIndex);
                float[] singlePt = new float[]{ selV[0], selV[1], selV[2] };

                ByteBuffer pbb = ByteBuffer.allocateDirect(12); 
                pbb.order(ByteOrder.nativeOrder());
                FloatBuffer ptBuf = pbb.asFloatBuffer();
                ptBuf.put(singlePt).position(0);

                GLES20.glVertexAttribPointer(pPositionHandle, 3, GLES20.GL_FLOAT, false, 0, ptBuf);

                GLES20.glUniform4f(pColorHandle, 1.0f, 0.0f, 0.0f, 1.0f); 
                GLES20.glUniform1f(pPointSizeHandle, 20.0f);
                GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1);

                GLES20.glDisableVertexAttribArray(pPositionHandle);
            }
        }
		
    }

    private Map<String, float[]> parseMTLFile(File mtlFile) throws Exception {
        Map<String, float[]> materials = new HashMap<>();
        if (mtlFile == null || !mtlFile.exists()) return materials;

        BufferedReader reader = new BufferedReader(new FileReader(mtlFile));
        String line;
        String currentMtl = null;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            int commentIdx = line.indexOf('#');
            if (commentIdx != -1) {
                line = line.substring(0, commentIdx).trim();
            }
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
                    materials.put(currentMtl, new float[]{r, g, b});
                } catch (NumberFormatException ignored) {}
            }
        }
        reader.close();
        return materials;
    }

    public void importFromFile(InputStream inputStream, String fileName) throws Exception {
        String parentPath = null;
        String pureName = fileName;
        if (fileName != null && fileName.contains("/")) {
            File f = new File(fileName);
            if (f.getParentFile() != null) parentPath = f.getParentFile().getAbsolutePath();
            pureName = f.getName();
        }
        importFromFile(inputStream, pureName, parentPath);
    }

    public void importFromFile(InputStream inputStream, String fileName, String parentDir) throws Exception {
        if (fileName != null && fileName.toLowerCase().endsWith(".mdl")) {
            importFromBinaryMDL(inputStream);
            return;
        }

        vertices.clear();
        texCoords.clear();
        normals.clear();
        triangles.clear();
        uvTriangles.clear();
        normalTriangles.clear();
        vertexColors.clear();
        faceColors.clear();
        hasVertexColors = false;
        hasMtlColors = false;

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;

        List<float[]> rawV = new ArrayList<>();
        List<float[]> rawVT = new ArrayList<>();
        List<int[]> rawFaces = new ArrayList<>();
        List<int[]> rawUVFaces = new ArrayList<>();
        List<float[]> rawVC = new ArrayList<>();
        List<float[]> rawFaceColors = new ArrayList<>();

        Map<String, float[]> mtlColors = new HashMap<>();
        float[] activeMtlColor = new float[]{0.75f, 0.75f, 0.8f};
        boolean foundMtlColor = false;
        boolean foundActualVC = false;
        boolean hasExplicitUVIndex = false;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("NUM_")) continue;

            int commentIdx = line.indexOf('#');
            if (commentIdx != -1) {
                line = line.substring(0, commentIdx).trim();
            }
            if (line.isEmpty()) continue;

            String[] tokens = line.split("\\s+");
            if (tokens.length == 0) continue;
            String head = tokens[0].toLowerCase();

            try {
                if (head.equals("mtllib") && tokens.length >= 2) {
					String mtlName = line.substring(line.indexOf(' ') + 1).trim();
					boolean loaded = false;
					if (parentDir != null && !parentDir.isEmpty()) {
						File mtlFile = new File(parentDir, mtlName);
						if (mtlFile.exists() && mtlFile.canRead()) {
							try {
								java.util.Map<String, float[]> parsed = parseMTLFile(mtlFile);
								mtlColors.putAll(parsed);
								lastMtlStatus = "MTL Loaded (" + parsed.size() + " mat): " + mtlFile.getName();
								loaded = true;
							} catch (Exception ignored) {}
						}
					}
				} else if (head.equals("usemtl") && tokens.length >= 2) {
					String matName = line.substring(line.indexOf(' ') + 1).trim();
					if (mtlColors.containsKey(matName)) {
						activeMtlColor = mtlColors.get(matName);
						foundMtlColor = true;
					} else if (!mtlColors.isEmpty()) {
						lastMtlStatus += " | Unmatched usemtl: " + matName;
					}
                } else if (head.equals("v")) {
                    if (tokens.length >= 4) {
                        rawV.add(new float[]{
									 Float.parseFloat(tokens[1]),
									 Float.parseFloat(tokens[2]),
									 Float.parseFloat(tokens[3])
								 });

                        if (tokens.length >= 7) {
                            float r = Float.parseFloat(tokens[4]);
                            float g = Float.parseFloat(tokens[5]);
                            float b = Float.parseFloat(tokens[6]);
                            if (r > 1.0f || g > 1.0f || b > 1.0f) {
                                r /= 255.0f; g /= 255.0f; b /= 255.0f;
                            }
                            rawVC.add(new float[]{r, g, b});
                            boolean isDummy = (r == 0f && g == 0f && b == 0f) || (r == 1f && g == 1f && b == 1f);
                            if (!isDummy) foundActualVC = true;
                        } else {
                            rawVC.add(new float[]{1.0f, 1.0f, 1.0f});
                        }
                    }
                } else if (head.equals("vt")) {
                    if (tokens.length >= 3) {
                        rawVT.add(new float[]{
									  Float.parseFloat(tokens[1]),
									  Float.parseFloat(tokens[2])
								  });
                    }
                } else if (head.equals("f")) {
                    if (tokens.length >= 4) {
                        int polyCount = tokens.length - 1;
                        int[] polyV = new int[polyCount];
                        int[] polyUV = new int[polyCount];

                        for (int i = 0; i < polyCount; i++) {
                            String token = tokens[i + 1];
                            if (token.contains("/")) {
                                String[] sub = token.split("/", -1);
                                if (!sub[0].isEmpty()) polyV[i] = Integer.parseInt(sub[0]);
                                if (sub.length > 1 && !sub[1].isEmpty()) {
                                    polyUV[i] = Integer.parseInt(sub[1]);
                                    hasExplicitUVIndex = true;
                                }
                            } else {
                                polyV[i] = Integer.parseInt(token);
                            }
                        }

                        for (int i = 1; i < polyCount - 1; i++) {
                            rawFaces.add(new int[]{polyV[0], polyV[i], polyV[i + 1]});
                            rawUVFaces.add(new int[]{polyUV[0], polyUV[i], polyUV[i + 1]});
                            rawFaceColors.add(new float[]{activeMtlColor[0], activeMtlColor[1], activeMtlColor[2]});
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        reader.close();

        if (rawV.isEmpty()) return;

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (float[] v : rawV) {
            if (v[0] < minX) minX = v[0];
            if (v[0] > maxX) maxX = v[0];
            if (v[1] < minY) minY = v[1];
            if (v[1] > maxY) maxY = v[1];
            if (v[2] < minZ) minZ = v[2];
            if (v[2] > maxZ) maxZ = v[2];
        }

        float centerX = (minX + maxX) * 0.5f;
        float centerY = (minY + maxY) * 0.5f;
        float centerZ = (minZ + maxZ) * 0.5f;

        float maxDimension = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        float scaleFactor = 2.6f;
        boolean shouldNormalize = (maxDimension > 3.5f || (maxDimension > 0.0001f && maxDimension < 0.2f));
        if (shouldNormalize) {
            scaleFactor = 2.6f / maxDimension;
        }

        for (int i = 0; i < rawV.size(); i++) {
            float[] v = rawV.get(i);
            float vx = shouldNormalize ? (v[0] - centerX) * scaleFactor : v[0];
            float vy = shouldNormalize ? (v[1] - centerY) * scaleFactor : v[1];
            float vz = shouldNormalize ? (v[2] - centerZ) * scaleFactor : v[2];

            vertices.add(new float[]{vx, vy, vz});
            normals.add(new float[]{0.0f, 1.0f, 0.0f});
            vertexColors.add(i < rawVC.size() ? rawVC.get(i) : new float[]{1.0f, 1.0f, 1.0f});
        }

        for (float[] vt : rawVT) {
            texCoords.add(new float[]{vt[0], vt[1]});
        }
        if (texCoords.isEmpty()) {
            texCoords.add(new float[]{0.5f, 0.5f});
        }

        boolean hasZeroVertexIndex = false;
        for (int[] f : rawFaces) {
            if (f[0] == 0 || f[1] == 0 || f[2] == 0) {
                hasZeroVertexIndex = true;
                break;
            }
        }
        int offset = (!rawFaces.isEmpty() && !hasZeroVertexIndex) ? 1 : 0;

        boolean hasZeroUVIndex = false;
        for (int[] uvf : rawUVFaces) {
            if (uvf[0] == 0 || uvf[1] == 0 || uvf[2] == 0) {
                hasZeroUVIndex = true;
                break;
            }
        }
        int uvOffset = (hasExplicitUVIndex && !hasZeroUVIndex) ? 1 : 0;

        for (int i = 0; i < rawFaces.size(); i++) {
            int[] f = rawFaces.get(i);
            int[] uvf = rawUVFaces.get(i);

            int v0 = (f[0] < 0) ? (rawV.size() + f[0]) : (f[0] - offset);
            int v1 = (f[1] < 0) ? (rawV.size() + f[1]) : (f[1] - offset);
            int v2 = (f[2] < 0) ? (rawV.size() + f[2]) : (f[2] - offset);

            int uv0, uv1, uv2;
            if (hasExplicitUVIndex) {
                uv0 = (uvf[0] < 0) ? (rawVT.size() + uvf[0]) : (uvf[0] - uvOffset);
                uv1 = (uvf[1] < 0) ? (rawVT.size() + uvf[1]) : (uvf[1] - uvOffset);
                uv2 = (uvf[2] < 0) ? (rawVT.size() + uvf[2]) : (uvf[2] - uvOffset);
            } else {
                uv0 = v0;
                uv1 = v1;
                uv2 = v2;
            }

            if (v0 >= 0 && v0 < vertices.size() &&
                v1 >= 0 && v1 < vertices.size() &&
                v2 >= 0 && v2 < vertices.size()) {

                if (isCCWMode) {
                    int temp = v1; v1 = v2; v2 = temp;
                    int tempUV = uv1; uv1 = uv2; uv2 = tempUV;
                }

                int[] tri = new int[]{v0, v1, v2};
                int[] uvTri = new int[]{
                    (uv0 >= 0 && uv0 < texCoords.size()) ? uv0 : 0,
                    (uv1 >= 0 && uv1 < texCoords.size()) ? uv1 : 0,
                    (uv2 >= 0 && uv2 < texCoords.size()) ? uv2 : 0
                };

                triangles.add(tri);
                uvTriangles.add(uvTri);
                normalTriangles.add(tri);
                if (i < rawFaceColors.size()) {
                    faceColors.add(rawFaceColors.get(i));
                }
            }
        }

        this.hasMtlColors = foundMtlColor;
        this.hasVertexColors = foundMtlColor || foundActualVC;

        recalculateNormals();
        updateBuffers();
    }

    public void switchWindingMode() {
        isCCWMode = !isCCWMode;
        for (int i = 0; i < triangles.size(); i++) {
            int[] face = triangles.get(i);
            int tempV = face[1];
            face[1] = face[2];
            face[2] = tempV;

            if (i < uvTriangles.size()) {
                int[] uv = uvTriangles.get(i);
                int tempUV = uv[1];
                uv[1] = uv[2];
                uv[2] = tempUV;
            }

            if (i < normalTriangles.size()) {
                int[] norm = normalTriangles.get(i);
                int tempN = norm[1];
                norm[1] = norm[2];
                norm[2] = tempN;
            }
        }
        updateBuffers();
    }

    public void importFromBinaryMDL(InputStream inputStream) throws Exception {
        vertices.clear();
        texCoords.clear();
        normals.clear();
        triangles.clear();
        uvTriangles.clear();
        normalTriangles.clear();
        vertexColors.clear();
        faceColors.clear();
        hasVertexColors = false;
        hasMtlColors = false;

        byte[] header = new byte[4];
        inputStream.read(header, 0, 4);
        String magic = new String(header, 0, 3);
        if (!magic.equals("MDL")) {
            throw new Exception("Header bukan format MDL Binary yang valid!");
        }

        int flags = inputStream.read();
        boolean hasV  = (flags & 0x01) != 0;
        boolean hasVT = (flags & 0x02) != 0;
        boolean hasVN = (flags & 0x04) != 0;
        boolean hasF  = (flags & 0x08) != 0;

        int numVertices = readLittleEndianInt(inputStream);
        int numFaces = readLittleEndianInt(inputStream);

        for (int i = 0; i < numVertices; i++) {
            float x = 0f, y = 0f, z = 0f;
            float u = 0.5f, v = 0.5f;
            float nx = 0f, ny = 1f, nz = 0f;

            if (hasV) {
                x = readLittleEndianFloat(inputStream);
                y = readLittleEndianFloat(inputStream);
                z = readLittleEndianFloat(inputStream);
            }
            if (hasVT) {
                u = readLittleEndianFloat(inputStream);
                v = readLittleEndianFloat(inputStream);
            }
            if (hasVN) {
                nx = readLittleEndianFloat(inputStream);
                ny = readLittleEndianFloat(inputStream);
                nz = readLittleEndianFloat(inputStream);
            }
            vertices.add(new float[]{x, y, z});
            texCoords.add(new float[]{u, v});
            normals.add(new float[]{nx, ny, nz});
            vertexColors.add(new float[]{1.0f, 1.0f, 1.0f});
        }

        if (hasF) {
            for (int i = 0; i < numFaces; i++) {
                int v1 = readLittleEndianShort(inputStream);
                int v2 = readLittleEndianShort(inputStream);
                int v3 = readLittleEndianShort(inputStream);

                if (isCCWMode) {
                    int temp = v2;
                    v2 = v3;
                    v3 = temp;
                }

                triangles.add(new int[]{v1, v2, v3});
                uvTriangles.add(new int[]{v1, v2, v3});
                normalTriangles.add(new int[]{v1, v2, v3});
            }
        }

        inputStream.close();
        updateBuffers();
    }

    private float readLittleEndianFloat(InputStream is) throws Exception {
        byte[] buffer = new byte[4];
        is.read(buffer, 0, 4);
        return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    private int readLittleEndianShort(InputStream is) throws Exception {
        byte[] buffer = new byte[2];
        is.read(buffer, 0, 2);
        return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
    }

    private int readLittleEndianInt(InputStream is) throws Exception {
        byte[] buffer = new byte[4];
        is.read(buffer, 0, 4);
        return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    public void exportToFile(File file, String format) throws Exception {
        if (format.equalsIgnoreCase("3d")) {
            exportTo3D(file);
        } else if (format.equalsIgnoreCase("mdl")) {
            exportToMDL(file, true, true, true);
        } else {
            exportToOBJ(file);
        }
    }

    private void exportTo3D(File file) throws Exception {
        FileWriter writer = new FileWriter(file);
        writer.write("# MiniBlender 3D ASCII Format\n");
        writer.write("NUM_VERTICES " + vertices.size() + "\n");
        for (int i = 0; i < vertices.size(); i++) {
            float[] v = vertices.get(i);
            float[] vt = (i < texCoords.size()) ? texCoords.get(i) : new float[]{0f, 0f};
            writer.write("v " + v[0] + " " + v[1] + " " + v[2] + " " + vt[0] + " " + vt[1] + "\n");
        }
        writer.write("NUM_FACES " + triangles.size() + "\n");
        for (int[] t : triangles) {
            writer.write("f " + t[0] + " " + t[1] + " " + t[2] + "\n");
        }
        writer.close();
    }

    private void exportToOBJ(File file) throws Exception {
        FileWriter writer = new FileWriter(file);
        writer.write("# MiniBlender Exported Wavefront OBJ\n");
        for (float[] v : vertices) {
            writer.write(String.format("v %.6f %.6f %.6f\n", v[0], v[1], v[2]));
        }
        for (float[] vt : texCoords) {
            writer.write(String.format("vt %.6f %.6f\n", vt[0], vt[1]));
        }
        for (float[] vn : normals) {
            writer.write(String.format("vn %.6f %.6f %.6f\n", vn[0], vn[1], vn[2]));
        }
        for (int i = 0; i < triangles.size(); i++) {
            int[] tri = triangles.get(i);
            int[] uv = (i < uvTriangles.size()) ? uvTriangles.get(i) : new int[]{0, 0, 0};
            int[] norm = (i < normalTriangles.size()) ? normalTriangles.get(i) : new int[]{0, 0, 0};

            writer.write(String.format("f %d/%d/%d %d/%d/%d %d/%d/%d\n", 
                                       tri[0]+1, uv[0]+1, norm[0]+1, 
                                       tri[1]+1, uv[1]+1, norm[1]+1, 
                                       tri[2]+1, uv[2]+1, norm[2]+1));
        }
        writer.close();
    }

    public void exportToMDL(File file, boolean includeUV, boolean includeNormal, boolean includeFaces) throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        DataOutputStream dos = new DataOutputStream(fos);

        dos.write(new byte[]{'M', 'D', 'L', 0x00});
        byte flags = 0x01;
        if (includeUV)     flags |= 0x02;
        if (includeNormal) flags |= 0x04;
        if (includeFaces)  flags |= 0x08;

        dos.writeByte(flags);
        writeLittleEndianInt(dos, vertices.size());
        writeLittleEndianInt(dos, includeFaces ? triangles.size() : 0);

        if (includeNormal) {
            recalculateNormals();
        }

        for (int i = 0; i < vertices.size(); i++) {
            float[] v = vertices.get(i);
            writeLittleEndianFloat(dos, v[0]);
            writeLittleEndianFloat(dos, v[1]);
            writeLittleEndianFloat(dos, v[2]);

            if (includeUV) {
                float[] vt = (i < texCoords.size()) ? texCoords.get(i) : new float[]{0f, 0f};
                writeLittleEndianFloat(dos, vt[0]);
                writeLittleEndianFloat(dos, vt[1]);
            }
            if (includeNormal) {
                float[] vn = (i < normals.size()) ? normals.get(i) : new float[]{0f, 1f, 0f};
                writeLittleEndianFloat(dos, vn[0]);
                writeLittleEndianFloat(dos, vn[1]);
                writeLittleEndianFloat(dos, vn[2]);
            }
        }

        if (includeFaces) {
            for (int[] t : triangles) {
                writeLittleEndianShort(dos, (short) t[0]);
                writeLittleEndianShort(dos, (short) t[1]);
                writeLittleEndianShort(dos, (short) t[2]);
            }
        }

        dos.flush();
        dos.close();
        fos.close();
    }

    private void writeLittleEndianFloat(DataOutputStream dos, float value) throws Exception {
        byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array();
        dos.write(bytes, 0, 4);
    }

    private void writeLittleEndianShort(DataOutputStream dos, short value) throws Exception {
        byte[] bytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array();
        dos.write(bytes, 0, 2);
    }

    private void writeLittleEndianInt(DataOutputStream dos, int value) throws Exception {
        byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
        dos.write(bytes, 0, 4);
    }
}

