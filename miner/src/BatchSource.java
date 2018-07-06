package snowblossom.miner;

import com.google.protobuf.ByteString;
import java.util.List;
import java.util.ArrayList;

public interface BatchSource
{
  public List<ByteString> readWordsBulk(List<Long> indexes);

}
