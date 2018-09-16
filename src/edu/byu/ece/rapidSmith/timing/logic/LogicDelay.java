package edu.byu.ece.rapidSmith.timing.logic;

import edu.byu.ece.rapidSmith.device.PrimitiveType;
import edu.byu.ece.rapidSmith.primitiveDefs.Connection;
import edu.byu.ece.rapidSmith.primitiveDefs.Element;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * Created by jakobw on 10.07.15.
 */
public class LogicDelay implements Serializable{
	private static final Logger logger = LoggerFactory.getLogger(LogicDelay.class);

	private static final long serialVersionUID = 5455492195199356515L;

    public final PrimitiveType type;
    public final String first;
    public final String second;
    final double delay;

    public LogicDelay(PrimitiveType type, String first, String second, double delay) {
        this.first = first;
        this.second = second;
        this.delay = delay;
        this.type = type;
    }

    public boolean enterToPrimitiveDef(PrimitiveDef prim) {

        Element first = prim.getElement(this.first);
        Element second = prim.getElement(this.second);

        if (first == null) {
        	logger.error("Did not find element {} in {}", this.first, prim);
        	return false;
		}
		if (second == null) {
			logger.error("Did not find element {} in {}", this.second, prim);
			return false;
		}

        Connection connFw = first.getConnectionTo(second);
        Connection connBw = second.getConnectionTo(first);

        if (connFw==null || connBw == null) {
        	logger.error("Connection not found between {} and {}", first, second);
			return false;
		}

        connFw.setDelay(delay);
        connBw.setDelay(delay);

        return true;
    }

    @Override
    public String toString() {
        return "LogicDelay{" +
                "type=" + type +
                ", first='" + first + '\'' +
                ", second='" + second + '\'' +
                ", delay=" + delay +
                '}';
    }
}
