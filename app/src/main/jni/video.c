#include <GLES/gl.h>
#include <GLES/glext.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>

#include <jni.h>
#include <string.h>
#include <stdio.h>
#include <pthread.h>
#include <android/log.h>

//ffmpeg video variables
int      initializedVideo=0;
int      initializedFrame=0;
AVFormatContext *pFormatCtx=NULL;
int             videoStream;
AVCodecContext  *pCodecCtx=NULL;
AVFrame         *pFrame=NULL;
AVPacket        packet;
int             got_picture;
float           aspect_ratio;

//ffmpeg video conversion variables
AVFrame         *pFrameConverted=NULL;
int             numBytes;
unsigned int *	rotate_data;
uint8_t         *bufferConverted=NULL;

//opengl
int textureFormat=AV_PIX_FMT_RGBA;//PIX_FMT_RGB24;
int textureWidth=256;
int textureHeight=256;
int nTextureHeight=-256;
static GLuint textureConverted=0;

//screen dimensions
int screenWidth = 50;
int screenHeight= 50;
//video
int dPaddingX=0,dPaddingY=0;
int drawWidth=50,drawHeight=50;

//file
const char * szFileName;
static uint8_t *video_dst_data[4] = {NULL};
static int      video_dst_linesize[4];
static int 		video_dst_bufsize;

static int 		bThreadStop = 1;
pthread_mutexattr_t	mta;
pthread_mutex_t* convertDataLock = NULL;
pthread_t 		rotateThread;
pthread_t 		convertThread;
unsigned int *	srcdata = NULL;
unsigned int *	data_buffer = NULL;
unsigned int *	dstdata = NULL;

AVInputFormat*	InputFmt;

int usecs = 0;

int64_t video_file_duration = 0;
int64_t video_video_duration = 0;

#define AV_TIME_BASE_CUSTOM 100000

void rotateProc(JNIEnv *pEnv) {

	__android_log_print(ANDROID_LOG_DEBUG, "rotateProc: ", "start");
	while(!bThreadStop) {
		int h = 0;
		int w = 0;

		pthread_mutex_lock(convertDataLock);
		if (bThreadStop) {pthread_mutex_unlock(convertDataLock); break;}
		memcpy(data_buffer, srcdata, textureHeight * textureWidth * 4);
		pthread_mutex_unlock(convertDataLock);

		if (bThreadStop) break;
		for (h = 0; h < textureHeight; h++)
		{
			for (w = 0; w < textureWidth; w++)
			{
				dstdata[w * textureHeight + h] = data_buffer[(textureHeight - h -1 ) * textureWidth + w];
			}
		}

		pthread_mutex_lock(convertDataLock);
		if (bThreadStop) {pthread_mutex_unlock(convertDataLock); break;}
		memcpy(rotate_data, dstdata, textureHeight * textureWidth * 4);
		pthread_mutex_unlock(convertDataLock);

		usleep(1);
	}
	__android_log_print(ANDROID_LOG_DEBUG, "rotateProc: ", "end");

}

void setRotateThread(JNIEnv *env) {
    bThreadStop = 0;
    pthread_create(&rotateThread, NULL, rotateProc, NULL);
}

void Java_com_tenistik_rendering_NativeCalls_initVideo
(JNIEnv * env, jobject this)  {
	initializedVideo = 0;
	initializedFrame = 0;
	usecs = 0;
}

static int open_codec_context(int *stream_idx,
                              AVFormatContext *fmt_ctx, enum AVMediaType type)
{
    int ret;
    AVStream *st;
    AVCodecContext *dec_ctx = NULL;
    AVCodec *dec = NULL;
    AVDictionary *opts = NULL;

    ret = av_find_best_stream(fmt_ctx, type, -1, -1, NULL, 0);
    if (ret < 0) {
    	__android_log_print(ANDROID_LOG_DEBUG, "Could not find %s stream in input file '%s'\n",
                av_get_media_type_string(type), szFileName);
        return ret;
    } else {
        *stream_idx = ret;
        st = fmt_ctx->streams[*stream_idx];

        /* find decoder for the stream */
        dec_ctx = st->codec;
        dec = avcodec_find_decoder(dec_ctx->codec_id);
        if (!dec) {
        	__android_log_print(ANDROID_LOG_DEBUG, "Failed to find %s codec\n",
                    av_get_media_type_string(type));
            return AVERROR(EINVAL);
        }

        /* Init the video decoder */
        av_dict_set(&opts, "flags2", "+export_mvs", 0);
        if ((ret = avcodec_open2(dec_ctx, dec, &opts)) < 0) {
        	__android_log_print(ANDROID_LOG_DEBUG, "Failed to open %s codec\n",
                    av_get_media_type_string(type));
            return ret;
        }
    }

    return 0;
}

/* list of things that get loaded: */
/* buffer */
/* pFrameConverted */
/* pFrame */
/* pCodecCtx */
/* pFormatCtx */
jint Java_com_tenistik_rendering_NativeCalls_loadVideo
(JNIEnv * env, jobject this, jstring fileName)  {
	jboolean isCopy;
	szFileName = (*env)->GetStringUTFChars(env, fileName, &isCopy);
	//debug
	__android_log_print(ANDROID_LOG_DEBUG, "NDK: ", "NDK:LC: [%s]", szFileName);
	// Register all formats and codecs
	av_register_all();
	// Open video file
	if(avformat_open_input(&pFormatCtx, szFileName, NULL, NULL)!=0) {
		__android_log_print(ANDROID_LOG_DEBUG,
				"video.c",
				"NDK: Couldn't open file");
		return -1;
	}
	__android_log_print(ANDROID_LOG_DEBUG, "video.c", "NDK: Succesfully loaded file");
	// Retrieve stream information */
	if(avformat_find_stream_info(pFormatCtx, NULL)<0) {
		__android_log_print(ANDROID_LOG_DEBUG,
				"video.c",
				"NDK: Couldn't find stream information");
		return -1;
	}
	__android_log_print(ANDROID_LOG_DEBUG, "video.c", "NDK: Found stream info");
	// Find the first video stream
	videoStream=-1;
	int i;
	for(i=0; i<pFormatCtx->nb_streams; i++)
		if(pFormatCtx->streams[i]->codec->codec_type==AVMEDIA_TYPE_VIDEO) {
			videoStream=i;
			break;
		}

    if (open_codec_context(&videoStream, pFormatCtx, AVMEDIA_TYPE_VIDEO) >= 0) {
    	pCodecCtx = pFormatCtx->streams[videoStream]->codec;
    }

	if(videoStream==-1) {
		__android_log_print(ANDROID_LOG_DEBUG,
				"video.c",
				"NDK: Didn't find a video stream");
		return -1;
	}
	__android_log_print(ANDROID_LOG_DEBUG, "video.c", "NDK: Found video stream");
	// Get a pointer to the codec contetx for the video stream
	pCodecCtx=pFormatCtx->streams[videoStream]->codec;

	// Allocate video frame (decoded pre-conversion frame)
	pFrame=avcodec_alloc_frame();
	// keep track of initialization
	initializedVideo = 1;
	__android_log_print(ANDROID_LOG_DEBUG,
			"video.c",
			"NDK: Finished loading video");


	/* dump file context */
	int hours, mins, secs, us;
	__android_log_print(ANDROID_LOG_DEBUG, "file context", "Dump File");
    if (pFormatCtx->duration != AV_NOPTS_VALUE) {
        int64_t duration = pFormatCtx->duration + 5000;
        secs  = duration / AV_TIME_BASE;
        us    = duration % AV_TIME_BASE;
        __android_log_print(ANDROID_LOG_DEBUG, "file context :", "duration %02d.%02d second", secs, (100 * us) / AV_TIME_BASE);
    } else {
        av_log(NULL, AV_LOG_INFO, "N/A");
    }
    if (pFormatCtx->start_time != AV_NOPTS_VALUE) {
        int secs, us;
        av_log(NULL, AV_LOG_INFO, ", start: ");
        secs = pFormatCtx->start_time / AV_TIME_BASE;
        us   = abs(pFormatCtx->start_time % AV_TIME_BASE);
        __android_log_print(ANDROID_LOG_DEBUG, "file context :", "start time %02d.%02d second", secs, (int) av_rescale(us, 1000000, AV_TIME_BASE));
    }

	video_video_duration = pFormatCtx->streams[videoStream]->duration;
	video_file_duration = pFormatCtx->duration + 5000;
	video_file_duration = video_file_duration / 10;

	__android_log_print(ANDROID_LOG_DEBUG, "getFrame", "%lld %lld", video_video_duration, video_file_duration);

    int dsec = (int)((video_file_duration) / 100);

    return dsec;
}

//for this to work, you need to set the scaled video dimensions first
void Java_com_tenistik_rendering_NativeCalls_prepareStorageFrame
(JNIEnv * env, jobject this)  {
	// Allocate an AVFrame structure
	pFrameConverted=avcodec_alloc_frame();
	// Determine required buffer size and allocate buffer
	numBytes=avpicture_get_size(textureFormat,
			textureWidth,
			textureHeight);
	bufferConverted=(uint8_t *)av_malloc(numBytes*sizeof(uint8_t));
	if ( pFrameConverted == NULL || bufferConverted == NULL )
		__android_log_print(ANDROID_LOG_DEBUG, "prepareStorage>>>>", "Out of memory");
	// Assign appropriate parts of buffer to image planes in pFrameRGB
	// Note that pFrameRGB is an AVFrame, but AVFrame is a superset
	// of AVPicture
	avpicture_fill((AVPicture *)pFrameConverted,
			bufferConverted,
			textureFormat,
			textureWidth,
			textureHeight);
	__android_log_print(ANDROID_LOG_DEBUG, "prepareStorage>>>>", "Created frame");
	__android_log_print(ANDROID_LOG_DEBUG, "prepareStorage>>>>", "texture dimensions: %dx%d", textureWidth, textureHeight);
	initializedFrame = 1;

	int ret = av_image_alloc(video_dst_data, video_dst_linesize,
			pCodecCtx->width, pCodecCtx->height, textureFormat, 1);
	if (ret < 0) {
		return;
	}
	video_dst_bufsize = ret;
	__android_log_print(ANDROID_LOG_DEBUG, "prepareStorage>>>>", "End frame");

	if (convertDataLock == NULL)
	{
		convertDataLock = malloc(sizeof(pthread_mutex_t));
		pthread_mutex_init( convertDataLock, NULL );
	}

	rotate_data = malloc(textureHeight * textureWidth * 4);
	dstdata = malloc(textureHeight * textureWidth * 4);
	srcdata = malloc(textureHeight * textureWidth * 4);
	data_buffer = malloc(textureHeight * textureWidth * 4);
}

jint Java_com_tenistik_rendering_NativeCalls_getVideoWidth
(JNIEnv * env, jobject this)  {
	return pCodecCtx->width;
}

jint Java_com_tenistik_rendering_NativeCalls_getVideoHeight
(JNIEnv * env, jobject this)  {
	return pCodecCtx->height;
}

jint Java_com_tenistik_rendering_NativeCalls_getFrame
(JNIEnv * env, jobject this)  {
	AVRational msec_ra;
	msec_ra.num = 1;
	msec_ra.den = 1000;

	// keep reading packets until we hit the end or find a video packet
	while(av_read_frame(pFormatCtx, &packet)>=0) {
		static struct SwsContext *img_convert_ctx;
		// Is this a packet from the video stream?
		if(packet.stream_index==videoStream) {
			// Decode video frame
			/* __android_log_print(ANDROID_LOG_DEBUG,  */
			/* 			  "video.c",  */
			/* 			  "getFrame: Try to decode frame" */
			/* 			  ); */
			avcodec_decode_video2(pCodecCtx,
					pFrame,
					&got_picture,
					&packet);
			// Did we get a video frame?
			if(got_picture) {
				if(img_convert_ctx == NULL) {
					/* get/set the scaling context */
					int w = pCodecCtx->width;
					int h = pCodecCtx->height;
#if 1
					img_convert_ctx =
							sws_getContext(
									w, h, //source
									pCodecCtx->pix_fmt,
									textureWidth,textureHeight,
									//w, h, //destination
									textureFormat,
									SWS_FAST_BILINEAR,
									NULL, NULL, NULL
							);
					if(img_convert_ctx == NULL) {
						/* __android_log_print(ANDROID_LOG_DEBUG,  */
						/* 			"video.c",  */
						/* 			"NDK: Cannot initialize the conversion context!" */
						/* 			); */
						return -1;
					}
#endif
				}

#if 1
				sws_scale(img_convert_ctx,
						pFrame->data,
						pFrame->linesize,
						0, pCodecCtx->height,
						pFrameConverted->data,
						pFrameConverted->linesize);
#else
	            av_image_copy(video_dst_data, video_dst_linesize,
	                          (const uint8_t **)(pFrame->data), pFrame->linesize,
	                          textureFormat, textureWidth, textureHeight);
#endif

	    		pthread_mutex_lock(convertDataLock);
	    #if 1
	    		memcpy(srcdata, pFrameConverted->data[0], textureHeight * textureWidth * 4);
	    #else
	    		memcpy(srcdata, video_dst_data[0], textureHeight * textureWidth * 4);
	    #endif
	    		pthread_mutex_unlock(convertDataLock);

				int64_t calc_pts = av_rescale(packet.pts, video_file_duration, video_video_duration);
	            usecs = (int)(calc_pts / 100);

				av_free_packet(&packet);
				return usecs;
			} /* if frame finished */
		} /* if packet video stream */
		// Free the packet that was allocated by av_read_frame
		av_free_packet(&packet);
	} /* while */
	//reload video when you get to the end
	//__android_log_print(ANDROID_LOG_DEBUG, "getFrame", "retry video");
	//av_seek_frame(pFormatCtx,videoStream,0,AVSEEK_FLAG_ANY);
	return -1;
}

void Java_com_tenistik_rendering_NativeCalls_setSeek
(JNIEnv * env, jobject this, jint usec) {
	//__android_log_print(ANDROID_LOG_DEBUG, "setSeek", "release thread 1");
	if (initializedVideo == 0)
		return;

	/* convert from usec to AV_TIME_BASE*/
	int64_t seek_pos = usec;
	seek_pos = av_rescale(seek_pos, video_video_duration, video_file_duration);
	seek_pos = seek_pos * 100;
	__android_log_print(ANDROID_LOG_DEBUG, "pts", "seek_pos : %lld %d", seek_pos, usec);
	av_seek_frame(pFormatCtx,videoStream,seek_pos,AVSEEK_FLAG_ANY);
}

void Java_com_tenistik_rendering_NativeCalls_closeVideo
(JNIEnv * env, jobject this) {
	__android_log_print(ANDROID_LOG_DEBUG, "closeVideo>>>>", "release thread");
	if ( initializedFrame == 1 ) {
		// Free the converted image
		av_free(bufferConverted);
		av_free(pFrameConverted);
		av_free(video_dst_data[0]);

		initializedFrame = 0;
		__android_log_print(ANDROID_LOG_DEBUG, "closeVideo>>>>", "Freed converted image");
	}
	if ( initializedVideo == 1 ) {
		/* // Free the YUV frame */
		av_free(pFrame);
		/* // Close the codec */
		avcodec_close(pCodecCtx);
		// Close the video file
		avformat_close_input(&pFormatCtx);
		__android_log_print(ANDROID_LOG_DEBUG, "closeVideo>>>>", "Freed video structures");
		initializedVideo = 0;
	}

	if (rotate_data)
	{
		free(rotate_data);
		rotate_data = NULL;
		free(dstdata);
		dstdata = NULL;
		free(srcdata);
		srcdata = NULL;
		free(data_buffer);
		data_buffer = NULL;
	}

	__android_log_print(ANDROID_LOG_DEBUG, "closeVideo>>>>", "Freed Buffers");
}

/*--- END OF VIDEO ----*/

/* disable these capabilities. */
static GLuint s_disable_options[] = {
		GL_FOG,
		GL_LIGHTING,
		GL_CULL_FACE,
		GL_ALPHA_TEST,
		GL_BLEND,
		GL_COLOR_LOGIC_OP,
		GL_DITHER,
		GL_STENCIL_TEST,
		GL_DEPTH_TEST,
		GL_COLOR_MATERIAL,
		0
};

void Java_com_tenistik_rendering_NativeCalls_initOpenGL
(JNIEnv * env, jobject this)  {
	__android_log_print(ANDROID_LOG_DEBUG, "NDK:", 	"initOpenGL()");
	__android_log_print(ANDROID_LOG_DEBUG, "NDK initOpenGL()", "texture dimensions: [%d]x[%d]", textureWidth, textureHeight);
	//Disable stuff
	__android_log_print(ANDROID_LOG_DEBUG, "NDK initOpenGL()", "disabling some opengl options");
	GLuint *start = s_disable_options;
	while (*start) glDisable(*start++);
	//setup textures
	__android_log_print(ANDROID_LOG_DEBUG, "NDK initOpenGL()", "enabling and generating textures");
	glEnable(GL_TEXTURE_2D);
	glGenTextures(1, &textureConverted);
	glBindTexture(GL_TEXTURE_2D,textureConverted);
	//...and bind it to our array
	__android_log_print(ANDROID_LOG_DEBUG, "NDK initOpenGL()", "binded texture");
	glTexParameterf(GL_TEXTURE_2D,
			GL_TEXTURE_MIN_FILTER,
			GL_NEAREST);
	glTexParameterf(GL_TEXTURE_2D,
			GL_TEXTURE_MAG_FILTER,
			GL_NEAREST);
	//Different possible texture parameters, e.g. GL10.GL_CLAMP_TO_EDGE
	glTexParameterf(GL_TEXTURE_2D,
			GL_TEXTURE_WRAP_S,
			GL_CLAMP_TO_EDGE);
	//GL_REPEAT);
	glTexParameterf(GL_TEXTURE_2D,
			GL_TEXTURE_WRAP_T,
			GL_CLAMP_TO_EDGE);
	//GL_REPEAT);
	glTexImage2D(GL_TEXTURE_2D,		/* target */
			0,			/* level */
			GL_RGBA,			/* internal format */
			textureHeight,		/* height */
			textureWidth,		/* width */
			//textureHeight,		/* height */
			0,			/* border */
			GL_RGBA,			/* format */
			GL_UNSIGNED_BYTE,/* type */
			NULL);
	//setup simple shading
	glShadeModel(GL_FLAT);
	//check_gl_error("glShademo_comdel");
	glColor4x(0x10000, 0x10000, 0x10000, 0x10000);

	setRotateThread(env);
}

void Java_com_tenistik_rendering_NativeCalls_drawFrame
(JNIEnv * env, jobject this)  {
	glClear(GL_COLOR_BUFFER_BIT);

	glBindTexture(GL_TEXTURE_2D,textureConverted);

	int rect[4] = {0, textureWidth, textureHeight, -textureWidth};
	//int rect[4] = {0, textureHeight, textureWidth, nTextureHeight};
	glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_CROP_RECT_OES, rect);

#if 0
	int h = 0, w = 0;
	for (h = 0; h < textureHeight; h++)
	{
		for (w = 0; w < textureWidth; w++)
		{
			rotate_data[w * textureHeight + h] = *(unsigned int *)(pFrameConverted->data[0] + (h * textureWidth + textureWidth - w - 1) * 4);
		}
	}
#endif

	//Reference:
	//http://old.siggraph.org/publications/2006cn/course16/KhronosSpecs/gl_egl_ref_1.1.20041110/glTexSubImage2D.html
	pthread_mutex_lock(convertDataLock);
	glTexSubImage2D(GL_TEXTURE_2D, /* target */
			0,		/* level */
			0,	/* xoffset */
			0,	/* yoffset */
			textureHeight,
			textureWidth,
			//textureHeight,
			GL_RGBA,	/* format */
			GL_UNSIGNED_BYTE, /* type */
			rotate_data);
	pthread_mutex_unlock(convertDataLock);

	//Reference:
	//http://old.siggraph.org/publications/2006cn/course16/KhronosSpecs/gl_egl_ref_1.1.20041110/glDrawTex.html
	glDrawTexiOES(0, 0, 0, screenWidth, screenHeight);
	//glDrawTexiOES(dPaddingX, dPaddingY, 0, drawWidth, drawHeight);
}

void Java_com_tenistik_rendering_NativeCalls_closeOpenGL
(JNIEnv *env, jobject this) {
	__android_log_print(ANDROID_LOG_DEBUG, "NDK closeOpenGL()", "closeOpenGL");
	glDeleteTextures(1, &textureConverted);
}

void Java_com_tenistik_rendering_NativeCalls_setScreenDimensions
(JNIEnv *env, jobject this, jint w, jint h) {
	screenWidth = w;
	screenHeight = h;
}

void Java_com_tenistik_rendering_NativeCalls_setTextureDimensions
(JNIEnv *env, jobject this, jint px, jint py) {
#if 1
	textureWidth = 720;
	textureHeight = 480;
	nTextureHeight = -1*480;
#else
	textureWidth = drawWidth;
	textureHeight = drawHeight;
	nTextureHeight = -1*drawHeight;
#endif
}

void Java_com_tenistik_rendering_NativeCalls_setDrawDimensions
(JNIEnv *env, jobject this, jint w, jint h) {
	drawWidth = w;
	drawHeight = h;
}

void Java_com_tenistik_rendering_NativeCalls_setScreenPadding
(JNIEnv *env, jobject this, jint w, jint h) {
	dPaddingX = w;
	dPaddingY = h;
}


void Java_com_tenistik_rendering_NativeCalls_setStopThread
(JNIEnv *env, jobject this) {
	if (bThreadStop)
		return;
	bThreadStop = 1;
	usleep(200);
	int status;
	pthread_mutex_lock(convertDataLock);
	if ( (status = pthread_kill(rotateThread, SIGUSR1)) != 0)
	{
		__android_log_print(ANDROID_LOG_DEBUG, "Error", "Stop Thread");
	}
	pthread_mutex_unlock(convertDataLock);
	//pthread_mutex_destroy(&convertDataLock);
}

extern int ffmpeg_main(int argc, char **argv);
#define MAX_ARGC 12
void Java_com_tenistik_rendering_NativeCalls_videoConvertToMjpeg
(JNIEnv * env, jobject this, jstring srcfileName, jstring dstfileName)  {
	jboolean isCopy;
	const char * srcFileName;
	const char * dstFileName;

	srcFileName = (*env)->GetStringUTFChars(env, srcfileName, &isCopy);
	dstFileName = (*env)->GetStringUTFChars(env, dstfileName, &isCopy);

	__android_log_print(ANDROID_LOG_DEBUG, "ffmpeg", "src %s", srcFileName);
	__android_log_print(ANDROID_LOG_DEBUG, "ffmpeg", "dst %s", dstFileName);
	//ffmpeg.exe -i srcFileName -an -vcodec mjpeg -f mp4 dstFileName

	char *argv[MAX_ARGC];
	int i = 0;
	for (i = 0; i < MAX_ARGC; i ++)
	{
		argv[i] = malloc(64);
		memset(argv[i], 0x0, 64);
	}

	memcpy(argv[0], "ffmpeg.exe", 11);
	memcpy(argv[1], "-y", 2);
	memcpy(argv[2], "-i", 2);
	memcpy(argv[3], srcFileName, strlen(srcFileName));
	memcpy(argv[4], "-an", 3);
	memcpy(argv[5], "-vcodec", 7);
	memcpy(argv[6], "mjpeg", 5);
	memcpy(argv[7], "-qscale", 7);
	memcpy(argv[8], "2", 1);
	memcpy(argv[9], "-f", 2);
	memcpy(argv[10], "mp4", 3);
	memcpy(argv[11], dstFileName, strlen(dstFileName));

	int ret = ffmpeg_main(MAX_ARGC, (char**)argv);

	__android_log_print(ANDROID_LOG_DEBUG, "ffmpeg", "ret %d", ret);

	for (i = 0; i < 10; i ++)
	{
		free(argv[i]);
	}
}