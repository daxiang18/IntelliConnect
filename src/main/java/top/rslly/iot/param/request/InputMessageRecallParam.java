package top.rslly.iot.param.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class InputMessageRecallParam {
  @NotBlank(message = "query 不能为空")
  @Size(min = 1, max = 2048, message = "query 长度必须在 1 到 2048 之间")
  private String query;

  @Size(max = 255, message = "sessionId 长度不能超过 255")
  private String sessionId;

  @Size(max = 50, message = "documentPurpose 长度不能超过 50")
  private String documentPurpose;
}
