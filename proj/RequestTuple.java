/**
 * A generic hash key for storing request tuples.
 *
 * To implement wildcards, set the foreign port and
 * address equal to -1
 */ 
import java.util.*;
import java.nio.*;

public class RequestTuple {
    public final int foreignAddress;
    public final int foreignPort;
    public final int localAddress;
    public final int localPort;

    private int startSeq;

    public RequestTuple(int foreignAddress, int foreignPort, int localAddress, int localPort, int startSeq){
        this(foreignAddress, foreignPort, localAddress, localPort);
        this.startSeq = startSeq;
    }

    public RequestTuple(int foreignAddress, int foreignPort, int localAddress, int localPort){
        this.foreignAddress = foreignAddress;
        this.foreignPort = foreignPort;
        this.localAddress = localAddress;
        this.localPort = localPort;
    }

    public int getStartSeq(){
        return this.startSeq;
    }

    @Override
    public boolean equals(Object o){
        if (this == o)
            return true;

        if (!(o instanceof RequestTuple))
            return false;

        RequestTuple rt = (RequestTuple) o;
        
        boolean match = rt.foreignAddress == this.foreignAddress
            && rt.foreignPort == this.foreignPort
            && rt.localAddress == this.localAddress
            && rt.localPort == this.localPort;

        return match;
    }

    @Override
    public int hashCode(){
        int base = (localAddress + 2) * (localAddress + 2) * (foreignAddress + 2) * (foreignPort + 2);
        return base << 5 + base; // mult. by 33 quickly
    }
}