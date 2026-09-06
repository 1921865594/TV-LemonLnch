package com.tv.player;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.Surface;

/**
 * Hand-written Binder contract. Kept identical in the launcher and player apps.
 */
public interface IPlayerService extends IInterface {
    String DESCRIPTOR = "com.tv.player.IPlayerService";
    int TRANSACTION_setSurface = IBinder.FIRST_CALL_TRANSACTION;
    int TRANSACTION_startPreview = IBinder.FIRST_CALL_TRANSACTION + 1;
    int TRANSACTION_pausePreview = IBinder.FIRST_CALL_TRANSACTION + 2;
    int TRANSACTION_resumePreview = IBinder.FIRST_CALL_TRANSACTION + 3;
    int TRANSACTION_stopPreview = IBinder.FIRST_CALL_TRANSACTION + 4;
    int TRANSACTION_isPlaying = IBinder.FIRST_CALL_TRANSACTION + 5;
    int TRANSACTION_getCurrentPosition = IBinder.FIRST_CALL_TRANSACTION + 6;
    int TRANSACTION_registerCallback = IBinder.FIRST_CALL_TRANSACTION + 7;
    int TRANSACTION_unregisterCallback = IBinder.FIRST_CALL_TRANSACTION + 8;

    void setSurface(Surface surface) throws RemoteException;
    void startPreview(String url) throws RemoteException;
    void pausePreview() throws RemoteException;
    void resumePreview() throws RemoteException;
    void stopPreview() throws RemoteException;
    boolean isPlaying() throws RemoteException;
    long getCurrentPosition() throws RemoteException;
    void registerCallback(IPlayerCallback callback) throws RemoteException;
    void unregisterCallback(IPlayerCallback callback) throws RemoteException;

    abstract class Stub extends Binder implements IPlayerService {
        public Stub() { attachInterface(this, DESCRIPTOR); }

        public static IPlayerService asInterface(IBinder obj) {
            if (obj == null) return null;
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof IPlayerService) return (IPlayerService) iin;
            return new Proxy(obj);
        }

        @Override public IBinder asBinder() { return this; }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(DESCRIPTOR);
                return true;
            }
            switch (code) {
                case TRANSACTION_setSurface:
                    data.enforceInterface(DESCRIPTOR);
                    Surface surface = null;
                    if (data.readInt() != 0) surface = Surface.CREATOR.createFromParcel(data);
                    setSurface(surface);
                    reply.writeNoException();
                    return true;
                case TRANSACTION_startPreview:
                    data.enforceInterface(DESCRIPTOR);
                    startPreview(data.readString());
                    reply.writeNoException();
                    return true;
                case TRANSACTION_pausePreview:
                    data.enforceInterface(DESCRIPTOR);
                    pausePreview();
                    reply.writeNoException();
                    return true;
                case TRANSACTION_resumePreview:
                    data.enforceInterface(DESCRIPTOR);
                    resumePreview();
                    reply.writeNoException();
                    return true;
                case TRANSACTION_stopPreview:
                    data.enforceInterface(DESCRIPTOR);
                    stopPreview();
                    reply.writeNoException();
                    return true;
                case TRANSACTION_isPlaying:
                    data.enforceInterface(DESCRIPTOR);
                    boolean playing = isPlaying();
                    reply.writeNoException();
                    reply.writeInt(playing ? 1 : 0);
                    return true;
                case TRANSACTION_getCurrentPosition:
                    data.enforceInterface(DESCRIPTOR);
                    long position = getCurrentPosition();
                    reply.writeNoException();
                    reply.writeLong(position);
                    return true;
                case TRANSACTION_registerCallback:
                    data.enforceInterface(DESCRIPTOR);
                    registerCallback(IPlayerCallback.Stub.asInterface(data.readStrongBinder()));
                    reply.writeNoException();
                    return true;
                case TRANSACTION_unregisterCallback:
                    data.enforceInterface(DESCRIPTOR);
                    unregisterCallback(IPlayerCallback.Stub.asInterface(data.readStrongBinder()));
                    reply.writeNoException();
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static final class Proxy implements IPlayerService {
            private final IBinder remote;
            Proxy(IBinder remote) { this.remote = remote; }
            @Override public IBinder asBinder() { return remote; }

            @Override public void setSurface(Surface surface) throws RemoteException {
                Parcel data = Parcel.obtain(); Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (surface != null) { data.writeInt(1); surface.writeToParcel(data, 0); } else data.writeInt(0);
                    remote.transact(TRANSACTION_setSurface, data, reply, 0); reply.readException();
                } finally { reply.recycle(); data.recycle(); }
            }
            @Override public void startPreview(String url) throws RemoteException {
                Parcel data = Parcel.obtain(); Parcel reply = Parcel.obtain();
                try { data.writeInterfaceToken(DESCRIPTOR); data.writeString(url); remote.transact(TRANSACTION_startPreview, data, reply, 0); reply.readException(); }
                finally { reply.recycle(); data.recycle(); }
            }
            @Override public void pausePreview() throws RemoteException {
                Parcel data = Parcel.obtain(); Parcel reply = Parcel.obtain();
                try { data.writeInterfaceToken(DESCRIPTOR); remote.transact(TRANSACTION_pausePreview, data, reply, 0); reply.readException(); }
                finally { reply.recycle(); data.recycle(); }
            }
            @Override public void resumePreview() throws RemoteException {
                Parcel data = Parcel.obtain(); Parcel reply = Parcel.obtain();
                try { data.writeInterfaceToken(DESCRIPTOR); remote.transact(TRANSACTION_resumePreview, data, reply, 0); reply.readException(); }
                finally { reply.recycle(); data.recycle(); }
            }
            @Override public void stopPreview() throws RemoteException {
                Parcel data = Parcel.obtain(); Parcel reply = Parcel.obtain();
                try { data.writeInterfaceToken(DESCRIPTOR); remote.transact(TRANSACTION_stopPreview, data, reply, 0); reply.readException(); }
                finally { reply.recycle(); data.recycle(); }
            }
            @Override public boolean isPlaying() throws RemoteException {
                Parcel data = Parcel.obtain(); Parcel reply = Parcel.obtain();
                try { data.writeInterfaceToken(DESCRIPTOR); remote.transact(TRANSACTION_isPlaying, data, reply, 0); reply.readException(); return reply.readInt() != 0; }
                finally { reply.recycle(); data.recycle(); }
            }
            @Override public long getCurrentPosition() throws RemoteException {
                Parcel data = Parcel.obtain(); Parcel reply = Parcel.obtain();
                try { data.writeInterfaceToken(DESCRIPTOR); remote.transact(TRANSACTION_getCurrentPosition, data, reply, 0); reply.readException(); return reply.readLong(); }
                finally { reply.recycle(); data.recycle(); }
            }
            @Override public void registerCallback(IPlayerCallback callback) throws RemoteException {
                Parcel data = Parcel.obtain(); Parcel reply = Parcel.obtain();
                try { data.writeInterfaceToken(DESCRIPTOR); data.writeStrongBinder(callback == null ? null : callback.asBinder()); remote.transact(TRANSACTION_registerCallback, data, reply, 0); reply.readException(); }
                finally { reply.recycle(); data.recycle(); }
            }
            @Override public void unregisterCallback(IPlayerCallback callback) throws RemoteException {
                Parcel data = Parcel.obtain(); Parcel reply = Parcel.obtain();
                try { data.writeInterfaceToken(DESCRIPTOR); data.writeStrongBinder(callback == null ? null : callback.asBinder()); remote.transact(TRANSACTION_unregisterCallback, data, reply, 0); reply.readException(); }
                finally { reply.recycle(); data.recycle(); }
            }
        }
    }
}
