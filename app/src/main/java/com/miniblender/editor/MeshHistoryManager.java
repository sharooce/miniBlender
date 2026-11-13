package com.miniblender.editor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class MeshHistoryManager {

    private static class MeshSnapshot {
        final List<float[]> verticesCopy;
        final List<int[]> trianglesCopy;
        final int selectedVertex;

        MeshSnapshot(List<float[]> verts, List<int[]> tris, int selIdx) {
            verticesCopy = new ArrayList<float[]>(verts.size());
            for (float[] v : verts) {
                verticesCopy.add(new float[]{ v[0], v[1], v[2] });
            }
            trianglesCopy = new ArrayList<int[]>(tris.size());
            for (int[] t : tris) {
                trianglesCopy.add(new int[]{ t[0], t[1], t[2] });
            }
            selectedVertex = selIdx;
        }
    }

    private final LinkedList<MeshSnapshot> undoStack = new LinkedList<MeshSnapshot>();
    private final LinkedList<MeshSnapshot> redoStack = new LinkedList<MeshSnapshot>();
    private static final int MAX_STEPS = 10;

    // Simpan kondisi sebelum mesh diubah (geser, tambah, atau hapus vertex)
    public void saveState(MeshModel mesh, int currentSelectedVertex) {
        if (mesh == null) return;
        if (undoStack.size() >= MAX_STEPS) {
            undoStack.removeFirst();
        }
        undoStack.addLast(new MeshSnapshot(mesh.vertices, mesh.triangles, currentSelectedVertex));
        redoStack.clear();
    }

    // Hapus vertex aktif sekaligus otomatis simpan ke riwayat Undo & rapikan indeks segitiga
    public int removeVertexWithHistory(MeshModel mesh, int delIdx) {
        if (mesh == null || delIdx < 0 || delIdx >= mesh.vertices.size() || mesh.vertices.size() <= 3) {
            return delIdx;
        }

        saveState(mesh, delIdx);

        Iterator<int[]> it = mesh.triangles.iterator();
        while (it.hasNext()) {
            int[] tri = it.next();
            if (tri[0] == delIdx || tri[1] == delIdx || tri[2] == delIdx) {
                it.remove();
            } else {
                if (tri[0] > delIdx) tri[0]--;
                if (tri[1] > delIdx) tri[1]--;
                if (tri[2] > delIdx) tri[2]--;
            }
        }

        mesh.vertices.remove(delIdx);
        mesh.rebuildBuffers();

        if (mesh.vertices.isEmpty()) return -1;
        return Math.min(delIdx, mesh.vertices.size() - 1);
    }

    // Mundur 1 langkah (maksimal 10 langkah)
    public int undo(MeshModel mesh, int currentSelectedVertex) {
        if (mesh == null || undoStack.isEmpty()) return currentSelectedVertex;

        if (redoStack.size() >= MAX_STEPS) {
            redoStack.removeFirst();
        }
        redoStack.addLast(new MeshSnapshot(mesh.vertices, mesh.triangles, currentSelectedVertex));

        MeshSnapshot prev = undoStack.removeLast();
        applySnapshot(mesh, prev);
        return prev.selectedVertex;
    }

    // Maju 1 langkah
    public int redo(MeshModel mesh, int currentSelectedVertex) {
        if (mesh == null || redoStack.isEmpty()) return currentSelectedVertex;

        if (undoStack.size() >= MAX_STEPS) {
            undoStack.removeFirst();
        }
        undoStack.addLast(new MeshSnapshot(mesh.vertices, mesh.triangles, currentSelectedVertex));

        MeshSnapshot next = redoStack.removeLast();
        applySnapshot(mesh, next);
        return next.selectedVertex;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    private void applySnapshot(MeshModel mesh, MeshSnapshot snap) {
        mesh.vertices.clear();
        for (float[] v : snap.verticesCopy) {
            mesh.vertices.add(new float[]{ v[0], v[1], v[2] });
        }
        mesh.triangles.clear();
        for (int[] t : snap.trianglesCopy) {
            mesh.triangles.add(new int[]{ t[0], t[1], t[2] });
        }
        mesh.rebuildBuffers();
    }
}

