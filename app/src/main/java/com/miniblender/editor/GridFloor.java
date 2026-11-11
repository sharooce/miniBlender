package com.miniblender.editor;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GridFloor {

    private FloatBuffer vertexBuffer;
    private int program;
    private int aPositionHandle;
    private int uMVPMatrixHandle;
    private int uColorHandle;

    private int vertexCount;

    private final String vertexShaderCode =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 aPosition;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * aPosition;" +
        "}";

    private final String fragmentShaderCode =
        "precision mediump float;" +
        "uniform vec4 uColor;" +
        "void main() {" +
        "  gl_FragColor = uColor;" +
        "}";

    public GridFloor() {
        int size = 10;
        float step = 1.0f;
        float y = -1.0f;

        int numLines = (size * 2 + 1) * 2;
        float[] vertices = new float[numLines * 6];

        int idx = 0;
        for (int i = -size; i <= size; i++) {
            vertices[idx++] = i * step; vertices[idx++] = y; vertices[idx++] = -size * step;
            vertices[idx++] = i * step; vertices[idx++] = y; vertices[idx++] =  size * step;

            vertices[idx++] = -size * step; vertices[idx++] = y; vertices[idx++] = i * step;
            vertices[idx++] =  size * step; vertices[idx++] = y; vertices[idx++] = i * step;
        }

        vertexCount = vertices.length / 3;

        ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        uColorHandle = GLES20.glGetUniformLocation(program, "uColor");
    }

    public void draw(float[] mvpMatrix) {
        GLES20.glUseProgram(program);

        GLES20.glEnableVertexAttribArray(aPositionHandle);
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0);
        GLES20.glUniform4f(uColorHandle, 0.35f, 0.35f, 0.35f, 1.0f);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, vertexCount);

        GLES20.glDisableVertexAttribArray(aPositionHandle);
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
