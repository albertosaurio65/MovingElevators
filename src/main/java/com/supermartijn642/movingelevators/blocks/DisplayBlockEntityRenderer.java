package com.supermartijn642.movingelevators.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import com.supermartijn642.core.ClientUtils;
import com.supermartijn642.core.render.RenderUtils;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Created 13/02/2022 by SuperMartijn642
 */
public class DisplayBlockEntityRenderer implements BlockEntityRenderer<DisplayBlockEntity> {

    private static final double TEXT_RENDER_DISTANCE = 15 * 15;

    @Override
    public void render(DisplayBlockEntity entity, float partialTicks, PoseStack matrixStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlay){
        if(!entity.isBottomDisplay())
            return;

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutout());

        int height = entity.hasDisplayOnTop() ? 2 : 1;
        Level level = entity.getLevel();
        Direction facing = entity.getFacing();
        ElevatorGroup group = entity.getElevatorGroup();
        BlockPos frontPos = entity.getBlockPos().relative(facing);
        if(height == 1)
            combinedLight = LevelRenderer.getLightColor(level, frontPos);
        else if(level.getBlockState(frontPos).emissiveRendering(level, frontPos) || level.getBlockState(frontPos.above()).emissiveRendering(level, frontPos.above()))
            combinedLight = 15728880;
        else{
            int skyLight = Math.max(level.getBrightness(LightLayer.SKY, frontPos), level.getBrightness(LightLayer.SKY, frontPos.above()));
            int blockLight = Math.max(level.getBrightness(LightLayer.BLOCK, frontPos), level.getBrightness(LightLayer.BLOCK, frontPos.above()));
            int blockStateLight = Math.max(level.getBlockState(frontPos).getLightEmission(level, frontPos), level.getBlockState(frontPos.above()).getLightEmission(level, frontPos.above()));
            blockLight = Math.max(blockLight, blockStateLight);
            combinedLight = skyLight << 20 | blockLight << 4;
        }

        matrixStack.pushPose();

        matrixStack.translate(0.5, 0.5, 0.5);
        matrixStack.mulPose(new Quaternion(0, 180 - facing.toYRot(), 0, true));
        matrixStack.translate(-0.5, -0.5, -0.51);

        // render background
        if(height == 1)
            this.drawOverlayPart(matrixStack, buffer, combinedLight, combinedOverlay, facing, 0, 0, 1, 1, 0, 0, 32, 32);
        else
            this.drawOverlayPart(matrixStack, buffer, combinedLight, combinedOverlay, facing, 0, 0, 1, 2, 32, 0, 32, 64);

        int index = group.getFloorNumber(entity.getInputBlockEntity().getFloorLevel());
        int button_count = height == 1 ? DisplayBlock.BUTTON_COUNT : DisplayBlock.BUTTON_COUNT_BIG;
        int below = index;
        int above = group.getFloorCount() - index - 1;
        if(below < above){
            below = Math.min(below, button_count);
            above = Math.min(above, button_count * 2 - below);
        }else{
            above = Math.min(above, button_count);
            below = Math.min(below, button_count * 2 - above);
        }
        int startIndex = index - below;
        int total = below + 1 + above;

        // render buttons
        Vec3 buttonPos = new Vec3(entity.getBlockPos().getX() + 0.5, entity.getBlockPos().getY() + 0.5 * height - total * DisplayBlock.BUTTON_HEIGHT / 2d, entity.getBlockPos().getZ() + 0.5);
        Vec3 cameraPos = RenderUtils.getCameraPosition();

        matrixStack.pushPose();
        matrixStack.translate(0, 0.5 * height - total * DisplayBlock.BUTTON_HEIGHT / 2d, -0.002);
        matrixStack.scale(1, DisplayBlock.BUTTON_HEIGHT, 1);
        for(int i = 0; i < total; i++){
            DyeColor labelColor = group.getFloorDisplayColor(startIndex + i);
            this.drawOverlayPart(matrixStack, buffer, combinedLight, combinedOverlay, facing, 0, 0, 1, 1, startIndex + i == index ? 96 : 64, labelColor.getId() * 4, 32, 4);

            boolean drawText = cameraPos.distanceToSqr(buttonPos) < TEXT_RENDER_DISTANCE; // text rendering is VERY slow apparently, so only draw it within a certain distance
            if(drawText){
                matrixStack.pushPose();
                matrixStack.translate(18.5 / 32d, 0, 0);
                this.drawString(matrixStack, bufferSource, combinedLight, MovingElevatorsClient.formatFloorDisplayName(group.getFloorDisplayName(startIndex + i), startIndex + i));
                matrixStack.popPose();
            }
            matrixStack.translate(0, 1, 0);
            buttonPos = buttonPos.add(0, DisplayBlock.BUTTON_HEIGHT, 0);
        }
        matrixStack.popPose();

        // render platform dot
        if(group.isMoving()){
            double platformY = group.getCurrentY();
            if(platformY >= group.getFloorYLevel(0) && platformY < group.getFloorYLevel(group.getFloorCount() - 1)){
                double yOffset = 0.5 * height - total * DisplayBlock.BUTTON_HEIGHT / 2d;
                for(int i = 0; i < group.getFloorCount() - 1; i++){
                    int belowY = group.getFloorYLevel(i);
                    int aboveY = group.getFloorYLevel(i + 1);
                    if(platformY >= belowY && platformY < aboveY)
                        yOffset += (i + (platformY - belowY) / (aboveY - belowY)) * DisplayBlock.BUTTON_HEIGHT;
                }
                matrixStack.translate(1 - (27.5 / 32d + DisplayBlock.BUTTON_HEIGHT / 2d), yOffset, -0.003);
                matrixStack.scale(DisplayBlock.BUTTON_HEIGHT, DisplayBlock.BUTTON_HEIGHT, 1);
                this.drawOverlayPart(matrixStack, buffer, combinedLight, combinedOverlay, facing, 0, 0, 1, 1, 0, 32, 10, 10);
            }
        }

        matrixStack.popPose();
    }

    private void drawOverlayPart(PoseStack matrixStack, VertexConsumer buffer, int combinedLight, int combinedOverlay, Direction facing, float x, float y, float width, float height, int tX, int tY, int tWidth, int tHeight){
        Matrix4f matrix = matrixStack.last().pose();
        Matrix3f normalMatrix = matrixStack.last().normal();

        float minU = MovingElevatorsClient.OVERLAY_SPRITE.getU(tX / 8f), maxU = MovingElevatorsClient.OVERLAY_SPRITE.getU((tX + tWidth) / 8f);
        float minV = MovingElevatorsClient.OVERLAY_SPRITE.getV(tY / 8f), maxV = MovingElevatorsClient.OVERLAY_SPRITE.getV((tY + tHeight) / 8f);

        buffer.vertex(matrix, x, y + height, 0).color(255, 255, 255, 255).uv(maxU, minV).uv2(combinedLight).normal(normalMatrix, facing.getStepX(), facing.getStepY(), facing.getStepZ()).overlayCoords(combinedOverlay).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0).color(255, 255, 255, 255).uv(minU, minV).uv2(combinedLight).normal(normalMatrix, facing.getStepX(), facing.getStepY(), facing.getStepZ()).overlayCoords(combinedOverlay).endVertex();
        buffer.vertex(matrix, x + width, y, 0).color(255, 255, 255, 255).uv(minU, maxV).uv2(combinedLight).normal(normalMatrix, facing.getStepX(), facing.getStepY(), facing.getStepZ()).overlayCoords(combinedOverlay).endVertex();
        buffer.vertex(matrix, x, y, 0).color(255, 255, 255, 255).uv(maxU, maxV).uv2(combinedLight).normal(normalMatrix, facing.getStepX(), facing.getStepY(), facing.getStepZ()).overlayCoords(combinedOverlay).endVertex();
    }

    private void drawString(PoseStack matrixStack, MultiBufferSource buffer, int combinedLight, String s){
        if(s == null)
            return;
        Font fontRenderer = ClientUtils.getMinecraft().font;
        matrixStack.pushPose();
        matrixStack.translate(0, 0.07, -0.005);
        matrixStack.scale(-0.01f, -0.08f, 1);
        fontRenderer.draw(matrixStack, s, -fontRenderer.width(s) / 2f, -fontRenderer.lineHeight, 0xffffffff);
        matrixStack.popPose();
    }
}