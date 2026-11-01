package com.example.authapp.ui;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.VBox;
import java.util.*;
import java.util.function.Consumer;

/** Keeps a visible text/control anchor stable instead of retaining a changing scrollbar percentage. */
public final class ChatViewport {
    private record Anchor(Node node,double viewportY) {}
    private final Parent root;
    private final ScrollPane scroll;
    private final VBox messages;
    private final Consumer<Boolean> unreadIndicator;
    private Anchor anchor;
    private boolean following=true,unread,pending,stickToEnd,active=true,disposed,userMoved,adjusting;
    private Scene scene;
    private Node viewportNode;
    private final Runnable pulse=this::afterLayout;

    public ChatViewport(Parent root,ScrollPane scroll,VBox messages,Consumer<Boolean> unreadIndicator) {
        this.root=root;this.scroll=scroll;this.messages=messages;this.unreadIndicator=unreadIndicator;
        scroll.vvalueProperty().addListener((o,a,b)->{
            if(adjusting || pending || disposed)return;
            userMoved=true;pending=false;
            following=nearEnd();if(following)setUnread(false);
            Platform.requestNextPulse();
        });
        root.layoutBoundsProperty().addListener((o,a,b)->schedule(false,null,false));
        scroll.viewportBoundsProperty().addListener((o,a,b)->schedule(false,null,false));
        scroll.getContent().layoutBoundsProperty().addListener((o,a,b)->schedule(false,null,false));
        root.sceneProperty().addListener((o,old,value)->attach(value));attach(root.getScene());
        scroll.addEventFilter(ScrollEvent.SCROLL,e->userScroll());
        scroll.addEventFilter(KeyEvent.KEY_PRESSED,e->{if(Set.of(KeyCode.UP,KeyCode.DOWN,KeyCode.PAGE_UP,KeyCode.PAGE_DOWN,KeyCode.HOME,KeyCode.END).contains(e.getCode()))userScroll();});
        scroll.addEventFilter(MouseEvent.MOUSE_PRESSED,e->{
            if(e.getTarget() instanceof Node target)for(Node n=target;n!=null && n!=scroll;n=n.getParent())
                if(n instanceof ScrollBar){userScroll();break;}
        });
    }
    public void addMessage(Node message) {
        schedule(false,null,true);
        if(messages.getChildren().size()>100)messages.getChildren().remove(0,20);
        messages.getChildren().add(message);
        if(!following)setUnread(true);
    }
    /** Invoked before the next layout of an expanded/collapsed detail panel. */
    public void reflow(Node title) {schedule(true,title,true);}
    public void scrollToBottom() {
        if(disposed)return;
        following=true;userMoved=false;setUnread(false);
        schedule(false,null,false);stickToEnd=true;anchor=null;
    }
    public void setActive(boolean value) {active=value;if(value)schedule(false,null,false);}
    public void dispose() {disposed=true;pending=false;attach(null);}
    private void attach(Scene value) {
        if(scene!=null)scene.removePostLayoutPulseListener(pulse);
        scene=disposed?null:value;viewportNode=null;
        if(scene!=null){scene.addPostLayoutPulseListener(pulse);Platform.requestNextPulse();}
    }
    private void setUnread(boolean value){if(unread!=value){unread=value;unreadIndicator.accept(value);}}
    private void userScroll() {
        if(disposed)return;
        pending=false;stickToEnd=false;following=false;userMoved=true;Platform.requestNextPulse();
    }
    private void schedule(boolean reading,Node preferred,boolean captureNow) {
        if(disposed)return;
        if(reading)following=false;
        if(pending) {
            if(reading){stickToEnd=false;anchor=capture(preferred);}
            return;
        }
        if(reading || captureNow && userMoved){anchor=capture(preferred);userMoved=false;}
        stickToEnd=following;pending=true;Platform.requestNextPulse();
    }
    /** One correction/capture after JavaFX's normal layout, never a recursive CSS/layout pass per wheel tick. */
    private void afterLayout() {
        if(disposed || !active)return;
        if(userMoved) {
            userMoved=false;pending=false;following=nearEnd();anchor=following?null:capture(null);
            if(following)setUnread(false);return;
        }
        if(pending) {
            adjusting=true;
            try {
            if(stickToEnd)scroll.setVvalue(scroll.getVmax());
            else if(anchor!=null && visible(anchor.node())) {
                double contentY=anchor.node().localToScene(anchor.node().getLayoutBounds()).getMinY()
                        -scroll.getContent().localToScene(scroll.getContent().getLayoutBounds()).getMinY();
                double range=range();double fraction=range<=0?0:Math.max(0,Math.min(1,(contentY-anchor.viewportY())/range));
                scroll.setVvalue(scroll.getVmin()+fraction*(scroll.getVmax()-scroll.getVmin()));
            } else scroll.setVvalue(scroll.getVmin());
            following=stickToEnd || fits();pending=false;
            if(following){anchor=null;setUnread(false);}
            } finally {adjusting=false;}
        }
    }
    private Anchor capture(Node preferred) {
        if(viewportNode==null)viewportNode=scroll.lookup(".viewport");
        Node viewport=viewportNode;
        if(viewport==null || viewport.getScene()==null)return null;
        Bounds visible=viewport.localToScene(viewport.getLayoutBounds());
        if(preferred!=null && visible(preferred)) {
            double y=preferred.localToScene(preferred.getLayoutBounds()).getMinY();
            if(y>=visible.getMinY()-1 && y<visible.getMaxY())return new Anchor(preferred,y-visible.getMinY());
        }
        List<Node> candidates=new ArrayList<>();collect(messages,candidates,visible);
        return candidates.stream().filter(this::visible).filter(n->{
                    Bounds b=n.localToScene(n.getLayoutBounds());return b.getMaxY()>visible.getMinY()+1 && b.getMinY()<visible.getMaxY();})
                .min(Comparator.comparingDouble(n->n.localToScene(n.getLayoutBounds()).getMinY()))
                .map(n->new Anchor(n,n.localToScene(n.getLayoutBounds()).getMinY()-visible.getMinY())).orElse(null);
    }
    private boolean visible(Node node) {
        for(Node n=node;n!=null;n=n.getParent()) {
            if(!n.isVisible() || !n.isManaged())return false;
            if(n!=node && n instanceof TitledPane pane && !pane.isExpanded())return false;
            if(n==messages)return true;
        }
        return false;
    }
    private static void collect(Node node,List<Node> result,Bounds viewport) {
        if(!node.isVisible() || !node.isManaged())return;
        Bounds bounds=node.localToScene(node.getLayoutBounds());
        if(bounds.getMaxY()<=viewport.getMinY() || bounds.getMinY()>=viewport.getMaxY())return;
        if(node instanceof Labeled && !(node instanceof TitledPane))result.add(node);
        if(node instanceof TitledPane pane) {if(pane.isExpanded() && pane.getContent()!=null)collect(pane.getContent(),result,viewport);}
        else if(node instanceof Parent parent)parent.getChildrenUnmodifiable().forEach(n->collect(n,result,viewport));
    }
    private double range() {return Math.max(0,scroll.getContent().getLayoutBounds().getHeight()-scroll.getViewportBounds().getHeight());}
    private boolean fits() {return range()<2;}
    private boolean nearEnd(){double span=scroll.getVmax()-scroll.getVmin();return fits() || span<=0 || range()*(scroll.getVmax()-scroll.getVvalue())/span<=24;}
}
