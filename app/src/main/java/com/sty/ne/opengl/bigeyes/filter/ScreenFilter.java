package com.sty.ne.opengl.bigeyes.filter;

import android.content.Context;

import com.sty.ne.opengl.bigeyes.R;


/**
 * 只负责往屏幕上渲染
 */
public class ScreenFilter extends BaseFilter{


    public ScreenFilter(Context context) {
        super(context, R.raw.base_vetex, R.raw.base_fragment);
    }


}
