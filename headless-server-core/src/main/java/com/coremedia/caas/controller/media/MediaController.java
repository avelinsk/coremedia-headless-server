package com.coremedia.caas.controller.media;

import com.coremedia.caas.controller.base.AbstractController;
import com.coremedia.caas.monitoring.Metrics;
import com.coremedia.cap.common.Blob;
import com.coremedia.cap.common.IdHelper;
import com.coremedia.cap.content.Content;
import com.coremedia.cap.content.ContentRepository;
import com.coremedia.cap.content.ContentType;
import com.coremedia.cap.multisite.Site;
import com.coremedia.cap.transform.Breakpoint;
import com.coremedia.cap.transform.TransformImageService;
import com.coremedia.cap.transform.Transformation;
import com.coremedia.transform.NamedTransformBeanBlobTransformer;
import com.coremedia.transform.TransformedBlob;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS}, allowedHeaders = {"authorization", "content-type", "x-requested-with"})
@RequestMapping("/caas/v1/{tenantId}/sites/{siteId}/media")
@Api(value = "/caas/v1/{tenantId}/sites/{siteId}/media", tags = "Media", description = "Operations for media objects")
public class MediaController extends AbstractController {

  private static final String TIMER_NAME = "caas.media.timer";


  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private TransformImageService transformImageService;

  @Autowired
  private NamedTransformBeanBlobTransformer mediaTransformer;

  @Autowired
  private Metrics metrics;


  @ResponseBody
  @RequestMapping(value = "/{mediaId}/{propertyName}", method = RequestMethod.GET)
  @ApiOperation(
          value = "Media.Blob",
          notes = "Return the binary data of a media object.\n" +
                  "Images can be cropped and scaled according to the responsive image settings of the site.",
          response = Byte.class,
          responseContainer = "Array"
  )
  @ApiResponses(value = {
          @ApiResponse(code = 400, message = "Invalid tenant or site"),
          @ApiResponse(code = 404, message = "Media object not found")
  })
  public ResponseEntity getMedia(@ApiParam(value = "The tenant's unique ID", required = true) @PathVariable String tenantId,
                                 @ApiParam(value = "The site's unique ID", required = true) @PathVariable String siteId,
                                 @ApiParam(value = "The media object's numeric ID", required = true) @PathVariable int mediaId,
                                 @ApiParam(value = "The blob property name", required = true) @PathVariable String propertyName,
                                 @ApiParam(value = "The required ratio if requesting an image") @RequestParam(required = false) String ratio,
                                 @ApiParam(value = "The required minimum width if requesting an image") @RequestParam(required = false, defaultValue = "-1") int minWidth,
                                 @ApiParam(value = "The required minimum height if requesting an image") @RequestParam(required = false, defaultValue = "-1") int minHeight,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
    Site localizedSite = getLocalizedSite(tenantId, siteId);
    if (localizedSite == null) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return null;
    }
    Content content = contentRepository.getContent(IdHelper.formatContentId(mediaId));
    if (content == null || !siteService.isContentInSite(localizedSite, content)) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return null;
    }
    ContentType contentType = content.getType();
    Blob blob = content.getBlob(propertyName);
    if (blob != null) {
      if (contentType.isSubtypeOf("CMPicture")) {
        if (ratio != null && mediaTransformer != null) {
          return metrics.timer(() -> getTransformedData(content, propertyName, blob, ratio, minWidth, minHeight), TIMER_NAME, "tenant", tenantId, "site", siteId, "contentType", contentType.getName(), "ratio", ratio);
        } else {
          return metrics.timer(() -> getOriginalData(blob), TIMER_NAME, "tenant", tenantId, "site", siteId, "contentType", contentType.getName());
        }
      } else if (contentType.isSubtypeOf("CMMedia")) {
        return metrics.timer(() -> getOriginalData(blob), TIMER_NAME, "tenant", tenantId, "site", siteId, "contentType", contentType.getName());
      } else {
        LOG.warn("Unsupported media type requested: {}", contentType.getName());
        return null;
      }
    }
    return null;
  }


  private ResponseEntity getOriginalData(Blob blob) {
    return ResponseEntity.ok()
            .contentLength(blob.getSize())
            .contentType(MediaType.parseMediaType(blob.getContentType().toString()))
            .body(new InputStreamResource(blob.getInputStream()));
  }


  private ResponseEntity getTransformedData(Content content, String propertyName, Blob blob, String ratio, int minWidth, int minHeight) {
    Transformation transformation = transformImageService.getTransformation(content, ratio);
    if (transformation != null) {
      Breakpoint bestMatch = null;
      for (Breakpoint breakpoint : transformation.getBreakpoints()) {
        if (bestMatch == null) {
          bestMatch = breakpoint;
        } else if (bestMatch.getWidth() < minWidth && bestMatch.getWidth() < breakpoint.getWidth()) {
          bestMatch = breakpoint;
        } else if (bestMatch.getHeight() < minHeight && bestMatch.getHeight() < breakpoint.getHeight()) {
          bestMatch = breakpoint;
        }
      }
      if (bestMatch != null) {
        TransformedBlob transformedBlob = mediaTransformer.transform(new PictureWrapper(content, propertyName, transformImageService), transformation.getName());
        if (transformedBlob != null) {
          Blob transformedScaledBlob = transformImageService.transformWithDimensions(content, blob, transformedBlob, transformation.getName(), "jpg", bestMatch.getWidth(), bestMatch.getHeight());
          return ResponseEntity.ok()
                  .contentLength(transformedScaledBlob.getSize())
                  .contentType(MediaType.parseMediaType(transformedScaledBlob.getContentType().toString()))
                  .body(new InputStreamResource(transformedScaledBlob.getInputStream()));
        }
      }
    }
    LOG.warn("No transformation '{}' found for {}", ratio, content);
    return getOriginalData(blob);
  }
}
