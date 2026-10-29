package com.example.authapp.ui;

import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

/** Measures wrapped controls at the requested width, before the parent computes message height. */
public final class ChatActions extends FlowPane {
    public ChatActions(double horizontalGap,double verticalGap) {
        super(horizontalGap,verticalGap);setMinWidth(0);setMaxWidth(Double.MAX_VALUE);
    }
    private void fitChildren(double width) {
        if(width<0)return;
        double available=Math.max(0,width-snappedLeftInset()-snappedRightInset());
        getManagedChildren().forEach(child->{
            if(child instanceof Region region && region.getMaxWidth()!=available)region.setMaxWidth(available);
        });
    }
    @Override protected double computePrefHeight(double width) {fitChildren(width);return super.computePrefHeight(width);}
    @Override protected double computeMinHeight(double width) {fitChildren(width);return super.computeMinHeight(width);}
    @Override protected void layoutChildren() {fitChildren(getWidth());super.layoutChildren();}
}
