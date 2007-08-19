package org.simbrain.world.threedee;

import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.jme.bounding.BoundingBox;
import com.jme.math.FastMath;
import com.jme.math.Quaternion;
import com.jme.math.Vector3f;
import com.jme.renderer.Camera;
import com.jme.renderer.ColorRGBA;
import com.jme.renderer.Renderer;
import com.jme.scene.Node;
import com.jme.scene.shape.Box;

public class Agent extends MultipleViewElement<Node> {

    private final float rotationSpeed = 2.5f;
    private final float movementSpeed = 1f;
    private final Vector3f turnAxis = new Vector3f(0f, 1f, 0f);
    private final Quaternion rotQuat = new Quaternion();
    private Vector3f direction;
    private Vector3f location;
    private Environment environment;
    private float rot = 0;
    
    private SortedMap<Integer, Input> inputs = new TreeMap<Integer, Input>();
    
    public void addInput(Integer priority, Input input) {
        inputs.put(1, input);
    }

    void setEnvironment(Environment environment) {
        this.environment = environment;
    }
    
    @Override
    public void update() {
        for (Input input : inputs.values()) {
            if (input.actions.size() < 1) continue;
            
            input.doActions(this);
            
            break;
        }
        
        rot = (rot + 3600) % 360;
        rotQuat.fromAngleNormalAxis(rot * FastMath.DEG_TO_RAD, turnAxis);
        
        float height = environment.getFloorHeight(location);
        
        if (!Float.isNaN(height)) location.setY(height + 1);
        
        super.update();
    }
    
    @Override
    protected void initSpatial(Renderer renderer, Node spatial) {
        /* no implementation yet */
    }

    @Override
    public void init(Vector3f direction, Vector3f location) {
        this.direction = direction;
        this.location = location;
        
        super.init(direction, location);
    }
    
    @Override
    protected Node create() {
        Box b = new Box("box", new Vector3f(), 0.35f,0.25f,0.5f);
        b.setModelBound(new BoundingBox());
        b.updateModelBound();
        b.setDefaultColor(ColorRGBA.red);
        Node node = new Node("Player Node");
        node.attachChild(b);
        
        return node;
    }

    @Override
    public void updateSpatial(Node node) {
        node.setLocalRotation(rotQuat);
        node.setLocalTranslation(location);
    }
    
    public void render(Camera camera) {
        camera.setFrame(location, rotQuat);
        camera.update();
    }
    
    public static class Input
    {
        private Set<Action> actions = new HashSet<Action>();
        
        public void set(Action action) {
            actions.add(action);
        }
        
        public void clear(Action action) {
            actions.remove(action);
        }
        
        private void doActions(Agent agent) {
            for (Action action : actions) {
                action.doAction(agent);
            }
        }
    }
    
    public enum Action {
        LEFT {
            void doAction(Agent agent) {
                agent.rot += agent.rotationSpeed;
            }
        }, 
        
        RIGHT {
            void doAction(Agent agent) {
                agent.rot -= agent.rotationSpeed;
            }
        }, 
        
        FORWARD {
            void doAction(Agent agent) {
                agent.location = agent.location.add(
                    agent.direction.mult(agent.movementSpeed));
            }
        },
        
        BACKWARD {
            void doAction(Agent agent) {
                agent.location = agent.location.subtract(
                    agent.direction.mult(agent.movementSpeed));
            }
        };
        
        /**
         * do not call this from outside this class!
         */
        abstract void doAction(Agent agent);
    }
}
