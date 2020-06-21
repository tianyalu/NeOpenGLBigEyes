package com.sty.ne.opengl.bigeyes.filter;

import android.content.Context;

import com.sty.ne.opengl.bigeyes.R;

public class BigEyeFilter extends BaseFrameFilter {
    public BigEyeFilter(Context context, int vertexSourceId, int fragmentSourceId) {
        super(context, R.raw.base_vetex, R.raw.base_fragment);
    }

    @Override
    public int onDrawFrame(int textureId) {
        return textureId;
    }
}
