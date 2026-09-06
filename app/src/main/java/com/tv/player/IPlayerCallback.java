package com.tv.player;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Hand-written Binder contract. Kept identical in the launcher and player apps
 * so AndroidIDE/Build Tools do not need to compile AIDL sources.
 */
public interface IPlayerCallback extends IInterface {
    String DESCRIPTOR = "com.tv.player.IPlayerCallback";
    int TRANSACTION_onPlayError = IBinder.FIRST_CALL_TRANSACTION;
    int TRANSACTION_onPlayStateChanged = IBinder.FIRST_CALL_TRANSACTION + 1;

    void onPlayError(int errorCode, String errorMsg) throws RemoteException;
    void onPlayStateChanged(int state) throws RemoteException;

    abstract class Stub extends Binder implements IPlayerCallback {
        public Stub() { attachInterface(this, DESCRIPTOR); }

        public static IPlayerCallback asInterface(IBinder obj) {
            if (obj == null) return null;
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof IPlayerCallback) return (IPlayerCallback) iin;
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
                case TRANSACTION_onPlayError:
                    data.enforceInterface(DESCRIPTOR);
                    int errorCode = data.readInt();
                    String errorMsg = data.readString();
                    onPlayError(errorCode, errorMsg);
                    return true;
                case TRANSACTION_onPlayStateChanged:
                    data.enforceInterface(DESCRIPTOR);
                    onPlayStateChanged(data.readInt());
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static final class Proxy implements IPlayerCallback {
            private final IBinder remote;
            Proxy(IBinder remote) { this.remote = remote; }
            @Override public IBinder asBinder() { return remote; }

            @Override public void onPlayError(int errorCode, String errorMsg) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(errorCode);
                    data.writeString(errorMsg);
                    remote.transact(TRANSACTION_onPlayError, data, null, IBinder.FLAG_ONEWAY);
                } finally { data.recycle(); }
            }

            @Override public void onPlayStateChanged(int state) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(state);
                    remote.transact(TRANSACTION_onPlayStateChanged, data, reply, 0);
                    reply.readException();
                } finally { reply.recycle(); data.recycle(); }
            }
        }
    }
}
