package com.example.aoastream;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import  android.widget.TextView;
import android.content.Intent;
import android.util.Log;
import com.example.aoastream.AccessoryEngine.IEngineCallback;

import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {
    public static final String TAG = "MainActivity";
    private static  final String MIME_TYPE = "video/avc";
    private int width = 640;
    private int height = 480;

    private AccessoryEngine mEngine = null;

    // View that contains the Surface Texture
    private TextureView m_surface;

    private TextView mText;

    // Media decoder
    private MediaCodec m_codec;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find our desired SurfaceTexture to display the stream
        m_surface = (TextureView) findViewById(R.id.textureView);

        // Add the SurfaceTextureListener
        m_surface.setSurfaceTextureListener(this);

        mText = (TextView)findViewById(R.id.textView);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "handling intent action: " + intent.getAction());
        mText.setText("handling intent action: " + intent.getAction());
        if (mEngine == null) {
            mEngine = new AccessoryEngine(getApplicationContext(), mCallback);
        }
        mEngine.onNewIntent(intent);
        super.onNewIntent(intent);
    }

    @Override
    // Invoked when a TextureView's SurfaceTexture is ready for use
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        onNewIntent(getIntent());
    }

    @Override
    // Invoked when the SurfaceTexture's buffer size changed
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    // Invoked when the specified SurfaceTexture is about to be destroyed
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    // Invoked when the specified SurfaceTexture is updated through updateTextImage()
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private final IEngineCallback mCallback = new IEngineCallback() {
        @Override
        public void onDeviceDisconnected() {
            Log.d(TAG, "device physically disconnected");
        }

        @Override
        public void onConnectionEstablished() {
            Log.d(TAG, "device connected! ready to go!");
            try {
                m_codec = MediaCodec.createDecoderByType(MIME_TYPE);

                final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE,width,height);
                //format.setInteger(MediaFormat.KEY_BIT_RATE,  40000);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
                //format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

                byte[] header_sps = {(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,(byte)0x67,(byte)0x64,(byte)0x00,(byte)0x1E,(byte)0xAC,(byte)0x56,(byte)0x80,(byte)0xA0,(byte)0x3D,(byte)0xA1,(byte)0x00,(byte)0x00,(byte)0x03,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x00,(byte)0x03,(byte)0x00,(byte)0x3C,(byte)0xE0,(byte)0x40,(byte)0x01,(byte)0xE8,(byte)0x40,(byte)0x00,(byte)0xF4,(byte)0x26,(byte)0xD6,(byte)0x36,(byte)0x07,(byte)0x8A,(byte)0x14,(byte)0x94};

                byte[] header_pps = {(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,(byte)0x28,(byte)0xEE,(byte)0x3C,(byte)0xB0};

                format.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
                format.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));

                m_codec.configure(format, new Surface(m_surface.getSurfaceTexture()),null,0);
                m_codec.start();
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        @Override
        public void onConnectionClosed() {
            Log.d(TAG, "connection closed");
        }

        @Override
        public void onDataRecieved(byte[] data, int num) {
            Log.d(TAG, String.format("received %d bytes", num));
            mText.setText(String.format("received %d bytes", num));
            try {
                ByteBuffer[] inputBuffers = m_codec.getInputBuffers();
                int inputBufferIndex = m_codec.dequeueInputBuffer(0);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    inputBuffer.clear();
                    try{
                        inputBuffer.put(data, 0, num);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    m_codec.queueInputBuffer(inputBufferIndex, 0, num, 0, 0);
                }
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                int outputBufferIndex = m_codec.dequeueOutputBuffer(bufferInfo, 0);
                while (outputBufferIndex >= 0) {
                    //If a valid surface was specified when configuring the codec,
                    //passing true renders this output buffer to the surface.
                    m_codec.releaseOutputBuffer(outputBufferIndex, true);
                    outputBufferIndex = m_codec.dequeueOutputBuffer(bufferInfo, 0);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    };
}
