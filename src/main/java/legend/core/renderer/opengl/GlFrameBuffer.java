package legend.core.renderer.opengl;

import legend.core.renderer.FrameBuffer;
import legend.core.renderer.FrameBufferAttachment;

import static legend.core.GameEngine.RENDERER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30C.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30C.glGenFramebuffers;
import static org.lwjgl.opengl.GL20C.glDrawBuffers;
import static org.lwjgl.opengl.GL43C.glObjectLabel;

public class GlFrameBuffer implements FrameBuffer {
  public final String name;
  private final int id;

  GlFrameBuffer(final String name, final FrameBufferAttachment[] attachments) {
    this.name = name;
    final int oldFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING);

    this.id = glGenFramebuffers();
    this.bind();

    if(RENDERER.api().debugEnabled()) {
      glObjectLabel(GL_FRAMEBUFFER, this.id, name);
    }

    int colours = 0;
    for(final FrameBufferAttachment attachment : attachments) {
      final int attachmentType = switch(attachment.type) {
        case COLOUR -> GL_COLOR_ATTACHMENT0 + colours++;
        case DEPTH -> GL_DEPTH_ATTACHMENT;
      };

      glFramebufferTexture2D(GL_FRAMEBUFFER, attachmentType, GL_TEXTURE_2D, ((GlTexture)attachment.texture).id, 0);
    }

    final int[] drawBuffers = new int[colours];
    for(int i = 0; i < colours; i++) drawBuffers[i] = GL_COLOR_ATTACHMENT0 + i;
    glDrawBuffers(drawBuffers);

    if(glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
      throw new RuntimeException("Render buffer is not complete!");
    }

    glBindFramebuffer(GL_FRAMEBUFFER, oldFramebuffer);
  }

  @Override
  public void delete() {
    glDeleteFramebuffers(this.id);
  }

  @Override
  public void bind() {
    glBindFramebuffer(GL_FRAMEBUFFER, this.id);
  }

}
