package com.ayush.quietlibrary;

public class NetworkInterface extends BaseNetworkInterface{
    @Override
    protected void initSystem() throws ModemException {
        this.quietSystem.initOpenSL();
    }

    @Override
    protected boolean isLoopback() {
        return false;
    }

    public NetworkInterface(NetworkInterfaceConfig conf) throws ModemException {
        super(conf);
    }
}
