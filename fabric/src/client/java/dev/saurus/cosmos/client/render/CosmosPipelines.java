package dev.saurus.cosmos.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Cosmos render pipelines on Minecraft 26.3's RenderPearl API. Shaders live in assets/cosmos/shaders/core and are
 * compiled lazily by Minecraft's pipeline cache (so they go through the same GLSL path on OpenGL and Vulkan).
 */
public final class CosmosPipelines {
    private CosmosPipelines() {}

    public static final RenderPipeline STARS = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("cosmos", "pipeline/stars"))
            .withVertexShader(Identifier.fromNamespaceAndPath("cosmos", "core/cosmos_stars"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("cosmos", "core/cosmos_stars"))
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("CosmosStars", UniformType.UNIFORM_BUFFER).build())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .build();

    public static final RenderPipeline SPACE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("cosmos", "pipeline/space"))
            .withVertexShader(Identifier.fromNamespaceAndPath("cosmos", "core/cosmos_space"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("cosmos", "core/cosmos_space"))
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("CosmosSpace", UniformType.UNIFORM_BUFFER)
                    .withUniform("SkySampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .build())
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .build();
}
