package com.miniblender.editor;

import android.opengl.GLES20;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class MeshModel {
    public List<float[]> vertices = new ArrayList<>();
    public List<float[]> texCoords = new ArrayList<>();
    public List<float[]> normals = new ArrayList<>();
    public List<int[]> triangles = new ArrayList<>();
    public List<int[]> uvTriangles = new ArrayList<>();
    public List<int[]> normalTriangles = new ArrayList<>();

    private FloatBuffer vertexBuffer;
    private FloatBuffer uvBuffer;
    private FloatBuffer normalBuffer;

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

    public boolean isCCWMode = true; 
    private boolean hasNormalsInFile = false;

    // 1. Vertex Shader: Normal ditransformasikan terhadap uMVPMatrix agar konsisten terhadap layar
    private final String vertexShaderCode =
        "uniform mat4 uMVPMatrix;\n" +
        "uniform mat4 uModelMatrix;\n" +
        "attribute vec4 aPosition;\n" +
        "attribute vec2 aTexCoord;\n" +
        "attribute vec3 aNormal;\n" +
        "varying vec2 vTexCoord;\n" +
        "varying vec3 vNormal;\n" +
        "void main() {\n" +
        "  gl_Position = uMVPMatrix * aPosition;\n" +
        "  vTexCoord = aTexCoord;\n" +
        "  vNormal = normalize(mat3(uMVPMatrix) * aNormal);\n" +
        "}\n";

    // 2. Fragment Shader: Lighting dari Atas-Depan Layar
    private final String fragmentShaderCode =
        "precision mediump float;\n" +
        "varying vec2 vTexCoord;\n" +
        "varying vec3 vNormal;\n" +
        "uniform sampler2D uTexture;\n" +
        "uniform int uUseTexture;\n" +
        "uniform vec4 uColor;\n" +
        "uniform vec3 uLightDir;\n" +
        "void main() {\n" +
        "  vec3 norm = normalize(vNormal);\n" +
        "  vec3 lightDir = normalize(uLightDir);\n" +
        "  float diff = max(dot(norm, lightDir), 0.0);\n" +
        "  float intensity = 0.35 + (0.65 * diff);\n" +
        "  vec4 baseColor;\n" +
        "  if (uUseTexture == 1) {\n" +
        "    baseColor = texture2D(uTexture, vTexCoord);\n" +
        "  } else {\n" +
        "    baseColor = uColor;\n" +
        "  }\n" +
        "  gl_FragColor = vec4(baseColor.rgb * intensity, baseColor.a);\n" +
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
    }

    public int addNewVertex(float x, float y, float z) {
        vertices.add(new float[]{x, y, z});
        texCoords.add(new float[]{0.5f, 0.5f});
        normals.add(new float[]{0.0f, 1.0f, 0.0f});
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

        int totalFloats = triangles.size() * 3 * 3;
        float[] vertexData = new float[totalFloats];
        float[] normalData = new float[totalFloats];
        float[] uvData = new float[triangles.size() * 3 * 2];

        int vIdx = 0;
        int uvIdx = 0;

        for (int i = 0; i < triangles.size(); i++) {
            int[] tri = triangles.get(i);
            int[] uvTri = (i < uvTriangles.size()) ? uvTriangles.get(i) : new int[]{0, 0, 0};
            int[] normTri = (i < normalTriangles.size()) ? normalTriangles.get(i) : new int[]{0, 0, 0};

            for (int j = 0; j < 3; j++) {
                int vertexIndex = tri[j];
                if (vertexIndex >= 0 && vertexIndex < vertices.size()) {
                    float[] v = vertices.get(vertexIndex);
                    vertexData[vIdx] = v[0];
                    vertexData[vIdx + 1] = v[1];
                    vertexData[vIdx + 2] = v[2];
                }

                int normIndex = normTri[j];
                if (normIndex >= 0 && normIndex < normals.size()) {
                    float[] n = normals.get(normIndex);
                    normalData[vIdx] = n[0];
                    normalData[vIdx + 1] = n[1];
                    normalData[vIdx + 2] = n[2];
                } else {
                    normalData[vIdx] = 0.0f;
                    normalData[vIdx + 1] = 1.0f;
                    normalData[vIdx + 2] = 0.0f;
                }
                vIdx += 3;

                int uvIndex = uvTri[j];
                if (uvIndex >= 0 && uvIndex < texCoords.size()) {
                    float[] uv = texCoords.get(uvIndex);
                    uvData[uvIdx++] = uv[0];
                    uvData[uvIdx++] = uv[1];
                } else {
                    uvData[uvIdx++] = 0.0f;
                    uvData[uvIdx++] = 0.0f;
                }
            }
        }

        ByteBuffer vbb = ByteBuffer.allocateDirect(vertexData.length * 4);
        vbb.order(ByteOrder.nativeOrder());
        vertexBuffer = vbb.asFloatBuffer();
        vertexBuffer.put(vertexData);
        vertexBuffer.position(0);

        ByteBuffer nbb = ByteBuffer.allocateDirect(normalData.length * 4);
        nbb.order(ByteOrder.nativeOrder());
        normalBuffer = nbb.asFloatBuffer();
        normalBuffer.put(normalData);
        normalBuffer.position(0);

        ByteBuffer uvb = ByteBuffer.allocateDirect(uvData.length * 4);
        uvb.order(ByteOrder.nativeOrder());
        uvBuffer = uvb.asFloatBuffer();
        uvBuffer.put(uvData);
        uvBuffer.position(0);
    }

    public void draw(float[] mvpMatrix, float[] modelMatrix, int textureId, int selectedVertexIndex, boolean isEditMode) {
        if (vertexBuffer == null || uvBuffer == null || normalBuffer == null) return;

        // KUNCI DEPTH TEST DAN DEPTH MASK DULU
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDepthMask(true);

        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0);

        if (uLightDirHandle != -1) {
            // Arah Cahaya dari Atas-Depan Layar HP (X=0.0, Y=1.0, Z=1.0)
            GLES20.glUniform3f(uLightDirHandle, 0.0f, 1.0f, 1.0f);
        }

        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        GLES20.glEnableVertexAttribArray(normalHandle);
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer);

        GLES20.glEnableVertexAttribArray(textureCoordHandle);
        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, uvBuffer);

        if (textureId > 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glUniform1i(uTextureHandle, 0);
            GLES20.glUniform1i(uUseTextureHandle, 1);
        } else {
            GLES20.glUniform1i(uUseTextureHandle, 0);
            GLES20.glUniform4f(colorHandle, 0.75f, 0.75f, 0.8f, 1.0f);
        }

        // Render Bodi Poligon 3D
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL);
        GLES20.glPolygonOffset(1.0f, 1.0f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, triangles.size() * 3);
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);

        // Render Wireframe (Wireframe Oranye Khas Blender)
        if (isEditMode) {
            GLES20.glUniform1i(uUseTextureHandle, 0);
            GLES20.glUniform4f(colorHandle, 1.0f, 0.55f, 0.0f, 1.0f);
            for (int[] tri : triangles) {
                float[] lineVerts = {
                    vertices.get(tri[0])[0], vertices.get(tri[0])[1], vertices.get(tri[0])[2],
                    vertices.get(tri[1])[0], vertices.get(tri[1])[1], vertices.get(tri[1])[2],
                    vertices.get(tri[1])[0], vertices.get(tri[1])[1], vertices.get(tri[1])[2],
                    vertices.get(tri[2])[0], vertices.get(tri[2])[1], vertices.get(tri[2])[2],
                    vertices.get(tri[2])[0], vertices.get(tri[2])[1], vertices.get(tri[2])[2],
                    vertices.get(tri[0])[0], vertices.get(tri[0])[1], vertices.get(tri[0])[2]
                };
                ByteBuffer lbb = ByteBuffer.allocateDirect(lineVerts.length * 4);
                lbb.order(ByteOrder.nativeOrder());
                FloatBuffer lineBuf = lbb.asFloatBuffer();
                lineBuf.put(lineVerts);
                lineBuf.position(0);

                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, lineBuf);
                GLES20.glLineWidth(2.0f);
                GLES20.glDrawArrays(GLES20.GL_LINES, 0, 6);
            }
        }

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(normalHandle);
        GLES20.glDisableVertexAttribArray(textureCoordHandle);

        // Render Titik Vertex (GL_POINTS)
        if (isEditMode && pointProgram != 0) {
            GLES20.glUseProgram(pointProgram);
            GLES20.glUniformMatrix4fv(pMVPMatrixHandle, 1, false, mvpMatrix, 0);
            GLES20.glEnableVertexAttribArray(pPositionHandle);

            float[] vCoords = new float[vertices.size() * 3];
            for (int i = 0; i < vertices.size(); i++) {
                float[] v = vertices.get(i);
                vCoords[i * 3] = v[0];
                vCoords[i * 3 + 1] = v[1];
                vCoords[i * 3 + 2] = v[2];
            }
            ByteBuffer pbb = ByteBuffer.allocateDirect(vCoords.length * 4);
            pbb.order(ByteOrder.nativeOrder());
            FloatBuffer ptBuf = pbb.asFloatBuffer();
            ptBuf.put(vCoords).position(0);

            GLES20.glVertexAttribPointer(pPositionHandle, 3, GLES20.GL_FLOAT, false, 0, ptBuf);

            GLES20.glUniform4f(pColorHandle, 1.0f, 0.5f, 0.0f, 1.0f);
            GLES20.glUniform1f(pPointSizeHandle, 14.0f);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, vertices.size());

            if (selectedVertexIndex >= 0 && selectedVertexIndex < vertices.size()) {
                GLES20.glUniform4f(pColorHandle, 1.0f, 0.0f, 0.0f, 1.0f);
                GLES20.glUniform1f(pPointSizeHandle, 20.0f);
                GLES20.glDrawArrays(GLES20.GL_POINTS, selectedVertexIndex, 1);
            }

            GLES20.glDisableVertexAttribArray(pPositionHandle);
        }
    }

    public void importFromFile(InputStream inputStream, String fileName) throws Exception {
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

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;

        List<float[]> rawV = new ArrayList<>();
        List<float[]> rawVT = new ArrayList<>();
        List<int[]> rawFaces = new ArrayList<>();

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("NUM_")) continue;

            String[] tokens = line.split("\\s+");
            if (tokens.length == 0) continue;
            String head = tokens[0];

            try {
                if (head.equalsIgnoreCase("v")) {
                    if (tokens.length >= 6) {
                        rawV.add(new float[]{
                            Float.parseFloat(tokens[1]),
                            Float.parseFloat(tokens[2]),
                            Float.parseFloat(tokens[3])
                        });
                        rawVT.add(new float[]{
                            Float.parseFloat(tokens[4]),
                            Float.parseFloat(tokens[5])
                        });
                    } else if (tokens.length >= 4) {
                        rawV.add(new float[]{
                            Float.parseFloat(tokens[1]),
                            Float.parseFloat(tokens[2]),
                            Float.parseFloat(tokens[3])
                        });
                    }
                } else if (head.equalsIgnoreCase("vt")) {
                    if (tokens.length >= 3) {
                        rawVT.add(new float[]{
                            Float.parseFloat(tokens[1]),
                            Float.parseFloat(tokens[2])
                        });
                    }
                } else if (head.equalsIgnoreCase("f")) {
                    if (tokens.length >= 4) {
                        int[] fData = new int[3];
                        for (int i = 0; i < 3; i++) {
                            String token = tokens[i + 1];
                            if (token.contains("/")) {
                                String[] sub = token.split("/", -1);
                                if (!sub[0].isEmpty()) fData[i] = Integer.parseInt(sub[0]);
                            } else {
                                fData[i] = Integer.parseInt(token);
                            }
                        }
                        rawFaces.add(fData);
                    }
                }
            } catch (Exception ignored) {}
        }
        reader.close();

        if (rawV.isEmpty()) return;

        for (int i = 0; i < rawV.size(); i++) {
            float[] v = rawV.get(i);
            float u = (i < rawVT.size()) ? rawVT.get(i)[0] : 0.5f;
            float vt = (i < rawVT.size()) ? rawVT.get(i)[1] : 0.5f;

            vertices.add(new float[]{v[0], v[1], v[2]});
            texCoords.add(new float[]{u, vt});
            normals.add(new float[]{0.0f, 1.0f, 0.0f});
        }

        boolean isOneBased = false;
        for (int[] f : rawFaces) {
            if (f[0] >= rawV.size() || f[1] >= rawV.size() || f[2] >= rawV.size()) {
                isOneBased = true;
                break;
            }
        }
        int offset = isOneBased ? 1 : 0;

        for (int[] f : rawFaces) {
            int v0 = f[0] - offset;
            int v1 = f[1] - offset;
            int v2 = f[2] - offset;

            if (v0 >= 0 && v0 < vertices.size() &&
                v1 >= 0 && v1 < vertices.size() &&
                v2 >= 0 && v2 < vertices.size()) {

                if (isCCWMode) {
                    int temp = v1;
                    v1 = v2;
                    v2 = temp;
                }

                int[] tri = new int[]{v0, v1, v2};
                triangles.add(tri);
                uvTriangles.add(tri);
                normalTriangles.add(tri);
            }
        }

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
