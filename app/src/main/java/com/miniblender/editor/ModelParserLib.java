package com.miniblender.editor;

import android.opengl.Matrix;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import org.json.JSONArray;
import org.json.JSONObject;

public class ModelParserLib {

    public static void parseModel(InputStream inputStream, String fileName, String parentDir, MeshModel mesh) throws Exception {
        String lower = (fileName != null) ? fileName.toLowerCase() : "";

        if (lower.endsWith(".fbx")) {
            parseFBX(inputStream, mesh);
        } else if (lower.endsWith(".stl")) {
            parseSTL(inputStream, mesh);
        } else if (lower.endsWith(".ply")) {
            parsePLY(inputStream, mesh);
        } else if (lower.endsWith(".glb")) {
            parseGLB(inputStream, mesh);
        } else {
            // Default: .obj, .3d, .mdl ditangani parser bawaan MeshModel
            mesh.importFromFile(inputStream, fileName, parentDir);
            return;
        }

        // Normalisasi skala & posisi agar pas dengan grid viewport miniBlender
        normalizeAndFinalize(mesh);
    }

    // =========================================================================
    // 1. PARSER FBX (Mendukung FBX Binary 7100-7700 + Hierarchy & FBX ASCII)
    // =========================================================================
    private static class FbxGeom {
        long id;
        double[] verts;
        int[] indices;
        double[] uvs;
        int[] uvIndices;
    }

    private static class FbxModelNode {
        long id;
        long parentModelId = 0;
        float[] lclTrans = new float[]{0f, 0f, 0f};
        float[] lclRot = new float[]{0f, 0f, 0f};
        float[] lclScale = new float[]{1f, 1f, 1f};
        float[] cachedWorldMatrix = null;
    }

    private static void parseFBX(InputStream is, MeshModel mesh) throws Exception {
        byte[] rawBytes = readAllBytes(is);
        clearMesh(mesh);

        if (rawBytes.length >= 27 && new String(rawBytes, 0, 18).equals("Kaydara FBX Binary")) {
            parseFBXBinary(rawBytes, mesh);
        } else {
            parseFBXAscii(new String(rawBytes, "UTF-8"), mesh);
        }
    }

    private static void parseFBXBinary(byte[] data, MeshModel mesh) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        bb.position(23);
        int version = bb.getInt();
        boolean is64Bit = (version >= 7500);

        List<FbxGeom> geomList = new ArrayList<>();
        Map<Long, FbxModelNode> modelMap = new HashMap<>();
        Map<Long, Long> geomToModel = new HashMap<>();
        List<long[]> rawConnections = new ArrayList<>();

        int footerSize = is64Bit ? 25 : 13;
        while (bb.position() < data.length - footerSize) {
            if (!readFbxNodeFull(bb, is64Bit, geomList, modelMap, rawConnections)) {
                break;
            }
        }

        if (geomList.isEmpty()) {
            throw new Exception("Geometri mesh tidak ditemukan di dalam FBX Binary (versi " + version + ").");
        }

        Map<Long, FbxGeom> geomById = new HashMap<>();
        for (FbxGeom g : geomList) geomById.put(g.id, g);

        for (long[] conn : rawConnections) {
            long childId = conn[0];
            long parentId = conn[1];
            if (geomById.containsKey(childId) && modelMap.containsKey(parentId)) {
                geomToModel.put(childId, parentId);
            } else if (modelMap.containsKey(childId) && modelMap.containsKey(parentId)) {
                modelMap.get(childId).parentModelId = parentId;
            }
        }

        int vertexBaseOffset = 0;
        int uvBaseOffset = 0;

        for (FbxGeom g : geomList) {
            if (g.verts == null || g.indices == null) continue;

            float[] worldMat = new float[16];
            Matrix.setIdentityM(worldMat, 0);
            Long modelId = geomToModel.get(g.id);
            if (modelId != null && modelMap.containsKey(modelId)) {
                worldMat = computeFbxWorldMatrix(modelMap.get(modelId), modelMap, 0);
            }

            float[] inVec = new float[4];
            float[] outVec = new float[4];

            for (int i = 0; i + 2 < g.verts.length; i += 3) {
                inVec[0] = (float) g.verts[i];
                inVec[1] = (float) g.verts[i + 1];
                inVec[2] = (float) g.verts[i + 2];
                inVec[3] = 1.0f;

                Matrix.multiplyMV(outVec, 0, worldMat, 0, inVec, 0);

                mesh.vertices.add(new float[]{outVec[0], outVec[1], outVec[2]});
                mesh.normals.add(new float[]{0f, 1f, 0f});
                mesh.vertexColors.add(new float[]{1f, 1f, 1f});
            }

            int addedUVs = 0;
            if (g.uvs != null && g.uvs.length >= 2) {
                for (int i = 0; i + 1 < g.uvs.length; i += 2) {
                    mesh.texCoords.add(new float[]{(float) g.uvs[i], (float) g.uvs[i + 1]});
                    addedUVs++;
                }
            }
            if (addedUVs == 0) {
                mesh.texCoords.add(new float[]{0.5f, 0.5f});
                addedUVs = 1;
            }

            List<Integer> polyV = new ArrayList<>();
            List<Integer> polyUV = new ArrayList<>();

            for (int i = 0; i < g.indices.length; i++) {
                int idx = g.indices[i];
                boolean isEnd = idx < 0;
                int vIdx = (isEnd ? (~idx) : idx) + vertexBaseOffset;
                int rawUv = (g.uvIndices != null && i < g.uvIndices.length) ? g.uvIndices[i] : (isEnd ? (~idx) : idx);
                int uvIdx = clampIdx(rawUv + uvBaseOffset, mesh.texCoords.size());

                polyV.add(vIdx);
                polyUV.add(uvIdx);

                if (isEnd) {
                    for (int k = 1; k < polyV.size() - 1; k++) {
                        int v0 = polyV.get(0), v1 = polyV.get(k), v2 = polyV.get(k + 1);
                        int u0 = polyUV.get(0), u1 = polyUV.get(k), u2 = polyUV.get(k + 1);
                        if (mesh.isCCWMode) {
                            int t = v1; v1 = v2; v2 = t;
                            int tu = u1; u1 = u2; u2 = tu;
                        }
                        int[] tri = new int[]{v0, v1, v2};
                        mesh.triangles.add(tri);
                        mesh.uvTriangles.add(new int[]{u0, u1, u2});
                        mesh.normalTriangles.add(tri);
                    }
                    polyV.clear();
                    polyUV.clear();
                }
            }

            vertexBaseOffset += (g.verts.length / 3);
            uvBaseOffset += addedUVs;
        }
    }

    private static float[] computeFbxWorldMatrix(FbxModelNode node, Map<Long, FbxModelNode> modelMap, int depth) {
        if (node.cachedWorldMatrix != null) return node.cachedWorldMatrix;

        float[] localMat = new float[16];
        Matrix.setIdentityM(localMat, 0);
        Matrix.translateM(localMat, 0, node.lclTrans[0], node.lclTrans[1], node.lclTrans[2]);
        if (node.lclRot[2] != 0f) Matrix.rotateM(localMat, 0, node.lclRot[2], 0f, 0f, 1f);
        if (node.lclRot[1] != 0f) Matrix.rotateM(localMat, 0, node.lclRot[1], 0f, 1f, 0f);
        if (node.lclRot[0] != 0f) Matrix.rotateM(localMat, 0, node.lclRot[0], 1f, 0f, 0f);
        Matrix.scaleM(localMat, 0, node.lclScale[0], node.lclScale[1], node.lclScale[2]);

        if (node.parentModelId != 0 && modelMap.containsKey(node.parentModelId) && depth < 32) {
            float[] parentWorld = computeFbxWorldMatrix(modelMap.get(node.parentModelId), modelMap, depth + 1);
            float[] worldMat = new float[16];
            Matrix.multiplyMM(worldMat, 0, parentWorld, 0, localMat, 0);
            node.cachedWorldMatrix = worldMat;
            return worldMat;
        }

        node.cachedWorldMatrix = localMat;
        return localMat;
    }

    private static boolean readFbxNodeFull(
		ByteBuffer bb, boolean is64Bit,
		List<FbxGeom> geomList, Map<Long, FbxModelNode> modelMap,
		List<long[]> connections) throws Exception {

        long endOffset = is64Bit ? bb.getLong() : (bb.getInt() & 0xFFFFFFFFL);
        long numProps  = is64Bit ? bb.getLong() : (bb.getInt() & 0xFFFFFFFFL);
        long propListLen = is64Bit ? bb.getLong() : (bb.getInt() & 0xFFFFFFFFL);
        int nameLen = bb.get() & 0xFF;

        if (endOffset == 0) return false;

        byte[] nameBytes = new byte[nameLen];
        bb.get(nameBytes);
        String nodeName = new String(nameBytes, "UTF-8");

        int propsStart = bb.position();
        int footerSize = is64Bit ? 25 : 13;

        if (nodeName.equals("Geometry")) {
            long geomId = readFbxFirstLongProp(bb, numProps);
            bb.position((int) (propsStart + propListLen));

            FbxGeom geom = new FbxGeom();
            geom.id = geomId;
            while (bb.position() < endOffset - footerSize) {
                if (!readFbxGeomChild(bb, is64Bit, geom)) break;
            }
            geomList.add(geom);

        } else if (nodeName.equals("Model")) {
            long modelId = readFbxFirstLongProp(bb, numProps);
            bb.position((int) (propsStart + propListLen));

            FbxModelNode mNode = new FbxModelNode();
            mNode.id = modelId;
            while (bb.position() < endOffset - footerSize) {
                if (!readFbxModelChild(bb, is64Bit, mNode)) break;
            }
            modelMap.put(modelId, mNode);

        } else if (nodeName.equals("C") && numProps >= 3) {
            Object[] props = readFbxSimpleProps(bb, (int) numProps);
            if (props.length >= 3 && props[1] instanceof Long && props[2] instanceof Long) {
                connections.add(new long[]{(Long) props[1], (Long) props[2]});
            }
            bb.position((int) (propsStart + propListLen));

        } else {
            bb.position((int) (propsStart + propListLen));
            while (bb.position() < endOffset - footerSize) {
                if (!readFbxNodeFull(bb, is64Bit, geomList, modelMap, connections)) break;
            }
        }

        bb.position((int) endOffset);
        return true;
    }

    private static boolean readFbxGeomChild(ByteBuffer bb, boolean is64Bit, FbxGeom geom) throws Exception {
        long endOffset = is64Bit ? bb.getLong() : (bb.getInt() & 0xFFFFFFFFL);
        long numProps  = is64Bit ? bb.getLong() : (bb.getInt() & 0xFFFFFFFFL);
        long propListLen = is64Bit ? bb.getLong() : (bb.getInt() & 0xFFFFFFFFL);
        int nameLen = bb.get() & 0xFF;
        if (endOffset == 0) return false;

        byte[] nameBytes = new byte[nameLen];
        bb.get(nameBytes);
        String name = new String(nameBytes, "UTF-8");
        int propsStart = bb.position();
        int footerSize = is64Bit ? 25 : 13;

        if (numProps > 0) {
            if (name.equals("Vertices")) geom.verts = readFbxDoubleArrayProp(bb);
            else if (name.equals("PolygonVertexIndex")) geom.indices = readFbxIntArrayProp(bb);
            else if (name.equals("UV")) geom.uvs = readFbxDoubleArrayProp(bb);
            else if (name.equals("UVIndex")) geom.uvIndices = readFbxIntArrayProp(bb);
        }

        bb.position((int) (propsStart + propListLen));
        while (bb.position() < endOffset - footerSize) {
            if (!readFbxGeomChild(bb, is64Bit, geom)) break;
        }
        bb.position((int) endOffset);
        return true;
    }

    private static boolean readFbxModelChild(ByteBuffer bb, boolean is64Bit, FbxModelNode mNode) throws Exception {
        long endOffset = is64Bit ? bb.getLong() : (bb.getInt() & 0xFFFFFFFFL);
        long numProps  = is64Bit ? bb.getLong() : (bb.getInt() & 0xFFFFFFFFL);
        long propListLen = is64Bit ? bb.getLong() : (bb.getInt() & 0xFFFFFFFFL);
        int nameLen = bb.get() & 0xFF;
        if (endOffset == 0) return false;

        byte[] nameBytes = new byte[nameLen];
        bb.get(nameBytes);
        String name = new String(nameBytes, "UTF-8");
        int propsStart = bb.position();
        int footerSize = is64Bit ? 25 : 13;

        if (name.equals("P") && numProps >= 7) {
            Object[] props = readFbxSimpleProps(bb, (int) numProps);
            if (props[0] instanceof String) {
                String pName = (String) props[0];
                float x = toFloat(props[4]);
                float y = toFloat(props[5]);
                float z = toFloat(props[6]);
                if (pName.equals("Lcl Translation")) {
                    mNode.lclTrans = new float[]{x, y, z};
                } else if (pName.equals("Lcl Rotation")) {
                    mNode.lclRot = new float[]{x, y, z};
                } else if (pName.equals("Lcl Scaling")) {
                    mNode.lclScale = new float[]{x, y, z};
                }
            }
        }

        bb.position((int) (propsStart + propListLen));
        while (bb.position() < endOffset - footerSize) {
            if (!readFbxModelChild(bb, is64Bit, mNode)) break;
        }
        bb.position((int) endOffset);
        return true;
    }

    private static long readFbxFirstLongProp(ByteBuffer bb, long numProps) {
        if (numProps <= 0) return 0;
        char type = (char) bb.get();
        if (type == 'L') return bb.getLong();
        if (type == 'I') return bb.getInt();
        return 0;
    }

    private static Object[] readFbxSimpleProps(ByteBuffer bb, int count) throws Exception {
        Object[] res = new Object[count];
        for (int i = 0; i < count; i++) {
            char type = (char) bb.get();
            switch (type) {
                case 'Y': res[i] = bb.getShort(); break;
                case 'C': res[i] = bb.get(); break;
                case 'I': res[i] = bb.getInt(); break;
                case 'F': res[i] = bb.getFloat(); break;
                case 'D': res[i] = bb.getDouble(); break;
                case 'L': res[i] = bb.getLong(); break;
                case 'S':
                case 'R':
                    int len = bb.getInt();
                    byte[] b = new byte[len];
                    bb.get(b);
                    res[i] = (type == 'S') ? new String(b, "UTF-8") : b;
                    break;
                default:
                    return res;
            }
        }
        return res;
    }

    private static float toFloat(Object o) {
        if (o instanceof Double) return ((Double) o).floatValue();
        if (o instanceof Float) return (Float) o;
        if (o instanceof Integer) return ((Integer) o).floatValue();
        if (o instanceof Long) return ((Long) o).floatValue();
        return 0f;
    }

    private static byte[] unpackFbxArrayBytes(ByteBuffer bb, int elemSize) throws Exception {
        int arrayLen = bb.getInt();
        int encoding = bb.getInt();
        int compressedLen = bb.getInt();

        byte[] raw = new byte[compressedLen];
        bb.get(raw);

        if (encoding == 0) {
            return raw;
        } else {
            byte[] inflated = new byte[arrayLen * elemSize];
            Inflater inflater = new Inflater();
            inflater.setInput(raw);
            inflater.inflate(inflated);
            inflater.end();
            return inflated;
        }
    }

    private static double[] readFbxDoubleArrayProp(ByteBuffer bb) throws Exception {
        char type = (char) bb.get();
        if (type == 'd') {
            byte[] bytes = unpackFbxArrayBytes(bb, 8);
            ByteBuffer ab = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            double[] res = new double[bytes.length / 8];
            for (int i = 0; i < res.length; i++) res[i] = ab.getDouble();
            return res;
        } else if (type == 'f') {
            byte[] bytes = unpackFbxArrayBytes(bb, 4);
            ByteBuffer ab = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            double[] res = new double[bytes.length / 4];
            for (int i = 0; i < res.length; i++) res[i] = ab.getFloat();
            return res;
        }
        return null;
    }

    private static int[] readFbxIntArrayProp(ByteBuffer bb) throws Exception {
        char type = (char) bb.get();
        if (type == 'i') {
            byte[] bytes = unpackFbxArrayBytes(bb, 4);
            ByteBuffer ab = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int[] res = new int[bytes.length / 4];
            for (int i = 0; i < res.length; i++) res[i] = ab.getInt();
            return res;
        } else if (type == 'l') {
            byte[] bytes = unpackFbxArrayBytes(bb, 8);
            ByteBuffer ab = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int[] res = new int[bytes.length / 8];
            for (int i = 0; i < res.length; i++) res[i] = (int) ab.getLong();
            return res;
        }
        return null;
    }

    // --- B. DECODER FBX ASCII ---
    private static void parseFBXAscii(String content, MeshModel mesh) throws Exception {
        float[] rawVerts = extractFbxFloatArray(content, "Vertices:");
        int[] rawIndices = extractFbxIntArray(content, "PolygonVertexIndex:");
        float[] rawUVs = extractFbxFloatArray(content, "UV:");
        int[] rawUVIndices = extractFbxIntArray(content, "UVIndex:");

        if (rawVerts == null || rawVerts.length < 3 || rawIndices == null || rawIndices.length < 3) {
            throw new Exception("Data geometri tidak ditemukan di dalam file FBX ASCII.");
        }

        for (int i = 0; i + 2 < rawVerts.length; i += 3) {
            mesh.vertices.add(new float[]{rawVerts[i], rawVerts[i + 1], rawVerts[i + 2]});
            mesh.normals.add(new float[]{0f, 1f, 0f});
            mesh.vertexColors.add(new float[]{1f, 1f, 1f});
        }

        if (rawUVs != null && rawUVs.length >= 2) {
            for (int i = 0; i + 1 < rawUVs.length; i += 2) {
                mesh.texCoords.add(new float[]{rawUVs[i], rawUVs[i + 1]});
            }
        }
        if (mesh.texCoords.isEmpty()) {
            mesh.texCoords.add(new float[]{0.5f, 0.5f});
        }

        List<Integer> polyV = new ArrayList<>();
        List<Integer> polyUV = new ArrayList<>();

        for (int i = 0; i < rawIndices.length; i++) {
            int idx = rawIndices[i];
            boolean isEndOfPoly = idx < 0;
            int actualV = isEndOfPoly ? (~idx) : idx;
            int actualUV = (rawUVIndices != null && i < rawUVIndices.length) ? rawUVIndices[i] : actualV;

            polyV.add(actualV);
            polyUV.add(actualUV);

            if (isEndOfPoly) {
                for (int k = 1; k < polyV.size() - 1; k++) {
                    int v0 = polyV.get(0), v1 = polyV.get(k), v2 = polyV.get(k + 1);
                    int uv0 = clampIdx(polyUV.get(0), mesh.texCoords.size());
                    int uv1 = clampIdx(polyUV.get(k), mesh.texCoords.size());
                    int uv2 = clampIdx(polyUV.get(k + 1), mesh.texCoords.size());

                    if (mesh.isCCWMode) {
                        int tmp = v1; v1 = v2; v2 = tmp;
                        int tmpUv = uv1; uv1 = uv2; uv2 = tmpUv;
                    }
                    int[] tri = new int[]{v0, v1, v2};
                    mesh.triangles.add(tri);
                    mesh.uvTriangles.add(new int[]{uv0, uv1, uv2});
                    mesh.normalTriangles.add(tri);
                }
                polyV.clear();
                polyUV.clear();
            }
        }
    }

    private static float[] extractFbxFloatArray(String text, String key) {
        int keyPos = text.indexOf(key);
        if (keyPos == -1) return null;
        int aPos = text.indexOf("a:", keyPos);
        int endBrace = text.indexOf("}", aPos);
        if (aPos == -1 || endBrace == -1) return null;

        String numBlock = text.substring(aPos + 2, endBrace).replaceAll("\\s+", "");
        if (numBlock.isEmpty()) return null;
        String[] parts = numBlock.split(",");
        float[] res = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { res[i] = Float.parseFloat(parts[i]); } catch (Exception ignored) {}
        }
        return res;
    }

    private static int[] extractFbxIntArray(String text, String key) {
        int keyPos = text.indexOf(key);
        if (keyPos == -1) return null;
        int aPos = text.indexOf("a:", keyPos);
        int endBrace = text.indexOf("}", aPos);
        if (aPos == -1 || endBrace == -1) return null;

        String numBlock = text.substring(aPos + 2, endBrace).replaceAll("\\s+", "");
        if (numBlock.isEmpty()) return null;
        String[] parts = numBlock.split(",");
        int[] res = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { res[i] = Integer.parseInt(parts[i]); } catch (Exception ignored) {}
        }
        return res;
    }

    // =========================================================================
    // 2. PARSER STL (Mendukung STL Binary & STL ASCII)
    // =========================================================================
    private static void parseSTL(InputStream is, MeshModel mesh) throws Exception {
        byte[] data = readAllBytes(is);
        clearMesh(mesh);
        mesh.texCoords.add(new float[]{0.5f, 0.5f});

        boolean isAscii = false;
        if (data.length > 80) {
            String head = new String(data, 0, Math.min(80, data.length)).trim().toLowerCase();
            if (head.startsWith("solid") && new String(data, 0, Math.min(512, data.length)).contains("facet normal")) {
                isAscii = true;
            }
        }

        if (isAscii) {
            String text = new String(data, "UTF-8");
            Pattern p = Pattern.compile("vertex\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)");
            Matcher m = p.matcher(text);
            int vCount = 0;
            while (m.find()) {
                float x = Float.parseFloat(m.group(1));
                float y = Float.parseFloat(m.group(2));
                float z = Float.parseFloat(m.group(3));
                mesh.vertices.add(new float[]{x, y, z});
                mesh.normals.add(new float[]{0f, 1f, 0f});
                mesh.vertexColors.add(new float[]{1f, 1f, 1f});
                vCount++;
                if (vCount % 3 == 0) {
                    int[] tri = mesh.isCCWMode
                        ? new int[]{vCount - 3, vCount - 1, vCount - 2}
                        : new int[]{vCount - 3, vCount - 2, vCount - 1};
                    mesh.triangles.add(tri);
                    mesh.uvTriangles.add(new int[]{0, 0, 0});
                    mesh.normalTriangles.add(tri);
                }
            }
        } else {
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            bb.position(80);
            int triCount = bb.getInt();
            for (int i = 0; i < triCount; i++) {
                if (bb.remaining() < 50) break;
                bb.getFloat(); bb.getFloat(); bb.getFloat();
                int baseIdx = mesh.vertices.size();
                for (int v = 0; v < 3; v++) {
                    float x = bb.getFloat();
                    float y = bb.getFloat();
                    float z = bb.getFloat();
                    mesh.vertices.add(new float[]{x, y, z});
                    mesh.normals.add(new float[]{0f, 1f, 0f});
                    mesh.vertexColors.add(new float[]{1f, 1f, 1f});
                }
                bb.getShort();
                int[] tri = mesh.isCCWMode
                    ? new int[]{baseIdx, baseIdx + 2, baseIdx + 1}
                    : new int[]{baseIdx, baseIdx + 1, baseIdx + 2};
                mesh.triangles.add(tri);
                mesh.uvTriangles.add(new int[]{0, 0, 0});
                mesh.normalTriangles.add(tri);
            }
        }
    }

    // =========================================================================
    // 3. PARSER PLY (ASCII Polygon File Format)
    // =========================================================================
    private static void parsePLY(InputStream is, MeshModel mesh) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        clearMesh(mesh);

        int vertexCount = 0;
        int faceCount = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("element vertex")) {
                vertexCount = Integer.parseInt(line.split("\\s+")[2]);
            } else if (line.startsWith("element face")) {
                faceCount = Integer.parseInt(line.split("\\s+")[2]);
            } else if (line.equals("end_header")) {
                break;
            }
        }

        for (int i = 0; i < vertexCount; i++) {
            line = reader.readLine();
            if (line == null) break;
            String[] p = line.trim().split("\\s+");
            float x = Float.parseFloat(p[0]);
            float y = Float.parseFloat(p[1]);
            float z = Float.parseFloat(p[2]);
            mesh.vertices.add(new float[]{x, y, z});
            mesh.normals.add(new float[]{0f, 1f, 0f});
            mesh.vertexColors.add(new float[]{1f, 1f, 1f});
            if (p.length >= 5) {
                mesh.texCoords.add(new float[]{Float.parseFloat(p[3]), Float.parseFloat(p[4])});
            }
        }
        if (mesh.texCoords.isEmpty()) mesh.texCoords.add(new float[]{0.5f, 0.5f});

        for (int i = 0; i < faceCount; i++) {
            line = reader.readLine();
            if (line == null) break;
            String[] p = line.trim().split("\\s+");
            int n = Integer.parseInt(p[0]);
            for (int k = 1; k < n - 1; k++) {
                int v0 = Integer.parseInt(p[1]);
                int v1 = Integer.parseInt(p[k + 1]);
                int v2 = Integer.parseInt(p[k + 2]);
                if (mesh.isCCWMode) { int t = v1; v1 = v2; v2 = t; }
                int[] tri = new int[]{v0, v1, v2};
                mesh.triangles.add(tri);
                mesh.uvTriangles.add(new int[]{0, 0, 0});
                mesh.normalTriangles.add(tri);
            }
        }
        reader.close();
    }

    // =========================================================================
    // 4. PARSER GLB (glTF 2.0 Binary)
    // =========================================================================
    private static void parseGLB(InputStream is, MeshModel mesh) throws Exception {
        byte[] data = readAllBytes(is);
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int magic = bb.getInt();
        if (magic != 0x46546C67) throw new Exception("Header GLB tidak valid!");
        bb.getInt();
        bb.getInt();

        int jsonChunkLen = bb.getInt();
        int jsonChunkType = bb.getInt();
        byte[] jsonBytes = new byte[jsonChunkLen];
        bb.get(jsonBytes);
        JSONObject gltf = new JSONObject(new String(jsonBytes, "UTF-8"));

        int binOffset = bb.position() + 8;
        clearMesh(mesh);

        JSONArray meshes = gltf.getJSONArray("meshes");
        JSONObject primitive = meshes.getJSONObject(0).getJSONArray("primitives").getJSONObject(0);
        JSONObject attrs = primitive.getJSONObject("attributes");

        int posAccIdx = attrs.getInt("POSITION");
        float[] posData = readGltfFloatAccessor(gltf, bb, binOffset, posAccIdx, 3);
        for (int i = 0; i + 2 < posData.length; i += 3) {
            mesh.vertices.add(new float[]{posData[i], posData[i + 1], posData[i + 2]});
            mesh.normals.add(new float[]{0f, 1f, 0f});
            mesh.vertexColors.add(new float[]{1f, 1f, 1f});
        }

        if (attrs.has("TEXCOORD_0")) {
            float[] uvData = readGltfFloatAccessor(gltf, bb, binOffset, attrs.getInt("TEXCOORD_0"), 2);
            for (int i = 0; i + 1 < uvData.length; i += 2) {
                mesh.texCoords.add(new float[]{uvData[i], 1.0f - uvData[i + 1]});
            }
        }
        if (mesh.texCoords.isEmpty()) mesh.texCoords.add(new float[]{0.5f, 0.5f});

        int idxAccIdx = primitive.getInt("indices");
        int[] indices = readGltfIntAccessor(gltf, bb, binOffset, idxAccIdx);
        for (int i = 0; i + 2 < indices.length; i += 3) {
            int v0 = indices[i], v1 = indices[i + 1], v2 = indices[i + 2];
            if (mesh.isCCWMode) { int t = v1; v1 = v2; v2 = t; }
            int[] tri = new int[]{v0, v1, v2};
            mesh.triangles.add(tri);
            mesh.uvTriangles.add(mesh.texCoords.size() > 1 ? tri : new int[]{0, 0, 0});
            mesh.normalTriangles.add(tri);
        }
    }

    private static float[] readGltfFloatAccessor(JSONObject gltf, ByteBuffer bb, int binStart, int accIdx, int comps) throws Exception {
        JSONObject acc = gltf.getJSONArray("accessors").getJSONObject(accIdx);
        JSONObject bv = gltf.getJSONArray("bufferViews").getJSONObject(acc.getInt("bufferView"));
        int offset = binStart + bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0);
        int count = acc.getInt("count");
        float[] out = new float[count * comps];
        bb.position(offset);
        for (int i = 0; i < out.length; i++) out[i] = bb.getFloat();
        return out;
    }

    private static int[] readGltfIntAccessor(JSONObject gltf, ByteBuffer bb, int binStart, int accIdx) throws Exception {
        JSONObject acc = gltf.getJSONArray("accessors").getJSONObject(accIdx);
        JSONObject bv = gltf.getJSONArray("bufferViews").getJSONObject(acc.getInt("bufferView"));
        int offset = binStart + bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0);
        int count = acc.getInt("count");
        int compType = acc.getInt("componentType");
        int[] out = new int[count];
        bb.position(offset);
        for (int i = 0; i < count; i++) {
            out[i] = (compType == 5123) ? (bb.getShort() & 0xFFFF) : bb.getInt();
        }
        return out;
    }

    // =========================================================================
    // HELPER NORMALISASI & BUFFER
    // =========================================================================
    private static void clearMesh(MeshModel mesh) {
        mesh.vertices.clear();
        mesh.texCoords.clear();
        mesh.normals.clear();
        mesh.triangles.clear();
        mesh.uvTriangles.clear();
        mesh.normalTriangles.clear();
        mesh.vertexColors.clear();
        mesh.faceColors.clear();
        mesh.hasVertexColors = false;
        mesh.hasMtlColors = false;
    }

    private static void normalizeAndFinalize(MeshModel mesh) {
        if (mesh.vertices.isEmpty()) return;

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (float[] v : mesh.vertices) {
            if (v[0] < minX) minX = v[0]; if (v[0] > maxX) maxX = v[0];
            if (v[1] < minY) minY = v[1]; if (v[1] > maxY) maxY = v[1];
            if (v[2] < minZ) minZ = v[2]; if (v[2] > maxZ) maxZ = v[2];
        }

        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float cz = (minZ + maxZ) * 0.5f;
        float maxDim = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        boolean shouldNorm = (maxDim > 3.5f || (maxDim > 0.0001f && maxDim < 0.2f));
        float scale = shouldNorm ? (2.6f / maxDim) : 1.0f;

        if (shouldNorm) {
            for (float[] v : mesh.vertices) {
                v[0] = (v[0] - cx) * scale;
                v[1] = (v[1] - cy) * scale;
                v[2] = (v[2] - cz) * scale;
            }
        }

        mesh.recalculateNormals();
        mesh.updateBuffers();
    }

    private static int clampIdx(int idx, int size) {
        return (idx >= 0 && idx < size) ? idx : 0;
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        is.close();
        return baos.toByteArray();
    }
}

