package org.conflictdetector.app;

import org.onosproject.net.flow.*;
import java.util.*;

public class MyRule {
        
        public Long id;
        private FlowEntry flow;
        private boolean removable;

        public MyRule(Long id, FlowEntry flow, boolean removable) {
            this.id=id;
            this.flow = flow;
            this.removable = removable;
        }

        public FlowEntry getFlow() {
            return this.flow;
        }

        public void setFlow(FlowEntry flow) {
            this.flow = flow;
        }

        public boolean isRemovable() {
            return this.removable;
        }

        public void setRemovable(boolean removable) {
            this.removable = removable;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MyRule myRule = (MyRule) o;
            return Objects.equals(id, myRule.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(flow);
        }

}
 
