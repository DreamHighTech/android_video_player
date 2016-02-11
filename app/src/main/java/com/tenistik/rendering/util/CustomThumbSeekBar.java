package com.tenistik.rendering.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.widget.SeekBar;

import com.tenistik.rendering.R;
import com.tenistik.rendering.VideoRender;

public class CustomThumbSeekBar {
	private Context mContext;
	public CustomThumbSeekBar(Context mContext){
		this.mContext = mContext;
	}
	/**
	 * Draws the seekbar thumbs.
	 *
	 * @param CustomBar custom seek bar whose thumbs are transparent
	 */
	public void drawThumb(SeekBar CustomBar){
		 Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.thumb);
        //Copying the dimensions from the original bitmap
		 Bitmap bmp = bitmap.copy(Bitmap.Config.ARGB_8888, true);
         bmp.setHasAlpha(true);
         //filling the bitmap with the transparent color
         //bmp.eraseColor(Color.TRANSPARENT);
         Canvas c = new Canvas(bmp);
         //String text = Integer.toString(CustomBar.getProgress());
         int nPos = VideoRender.mSeekPos / 100;
         String text = String.valueOf(nPos/10.0f);
         Paint p = new Paint();
         p.setColor(Color.BLACK); 
         p.setTextSize(20);
         //calculating the dimensions of the bitmap to place the text over it
         int width = (int) p.measureText(text);
         int yPos = (int) ((c.getHeight() / 2) - ((p.descent() + p.ascent()) / 2));
         //placing the text over the bitmap
         c.drawText(text, (bmp.getWidth()-width)/2, yPos, p);
         CustomBar.setThumb(new BitmapDrawable(mContext.getResources(), bmp));
	}
}
