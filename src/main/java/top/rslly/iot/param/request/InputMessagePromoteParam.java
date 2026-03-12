package top.rslly.iot.param.request;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class InputMessagePromoteParam {
  @Min(value = 1, message = "productId 必须大于 0")
  private int productId;

  @NotBlank(message = "memoryKey 不能为空")
  @Size(min = 1, max = 255, message = "memoryKey 长度必须在 1 到 255 之间")
  private String memoryKey;

  @NotBlank(message = "description 不能为空")
  @Size(min = 1, max = 255, message = "description 长度必须在 1 到 255 之间")
  private String description;

  @Size(min = 1, max = 1024, message = "memoryValue 长度必须在 1 到 1024 之间")
  private String memoryValue;
}
