package snowblossom.client;

import java.io.File;
import duckutil.Config;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.TransactionUtil;
import snowblossom.proto.*;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import snowblossom.lib.TransactionBridge;

import com.google.common.collect.ImmutableList;


/** Where you keep your wallet i
 *
 * Used to coordinate writes to not get into weirdo states
 */
public class Purse
{
  private static final Logger logger = Logger.getLogger("snowblossom.client");

  private final File wallet_path;
  private final Config config;
  private final NetworkParams params;
  private final SnowBlossomClient client;

  private volatile WalletDatabase wallet_database;

  public Purse(SnowBlossomClient client, File wallet_path, Config config, NetworkParams params)
    throws Exception
  {
    this.wallet_path = wallet_path;
    this.config = config;
    this.params = params;
    this.client = client;

    wallet_database = WalletUtil.loadWallet(wallet_path, true, params);
    if (wallet_database == null)
    {
      logger.log(Level.WARNING, String.format("Directory %s does not contain wallet, creating new wallet", wallet_path.getPath()));
      wallet_database = WalletUtil.makeNewDatabase(config, params);
      WalletUtil.saveWallet(wallet_database, wallet_path);
    }

    wallet_database = WalletUtil.fillKeyPool(wallet_database, wallet_path, config, params);

  }

  public WalletDatabase getDB(){return wallet_database;}

  public synchronized void mergeIn(WalletDatabase merge)
    throws Exception
  {
    WalletUtil.saveWallet(merge, wallet_path);

    wallet_database = WalletUtil.mergeDatabases(ImmutableList.of(wallet_database, merge), params);

  }

	public synchronized void markUsed(AddressSpecHash hash)
    throws Exception
	{
    wallet_database = WalletUtil.markUsed(wallet_database, wallet_path, config, params, hash);
	}

  public AddressSpecHash getUnusedAddress(boolean mark_used, boolean generate_now)
    throws Exception
  {
    if (generate_now)
    {
      WalletDatabase.Builder partial_new_db = WalletDatabase.newBuilder();
      partial_new_db.setVersion(WalletUtil.WALLET_DB_VERSION);
      WalletUtil.genNewKey(partial_new_db, config, params);
      AddressSpecHash hash = AddressUtil.getHashForSpec(partial_new_db.getAddresses(0));

      if (mark_used)
      {
        String address = AddressUtil.getAddressString(params.getAddressPrefix(), hash);
        partial_new_db.putUsedAddresses(address, true);
      }

      WalletDatabase new_db_part = partial_new_db.build();
      WalletUtil.saveWallet(new_db_part, wallet_path);
      synchronized(this)
      {
        wallet_database = WalletUtil.mergeDatabases(ImmutableList.of(wallet_database, new_db_part), params);
      }
      return hash;
    }

    synchronized(this)
    {

      AddressSpecHash hash = WalletUtil.getOldestUnused(wallet_database, params);
      if (hash == null)
      {
        return getUnusedAddress(mark_used, true);
      }
      if (mark_used)
      {
        markUsed(hash);
       
      }
      return hash;    
    }

  }

  public synchronized Transaction send(List<TransactionOutput> out_list, long fee, boolean broadcast)
    throws Exception
  {
    boolean mark_used = broadcast;
    AddressSpecHash change = client.getPurse().getUnusedAddress(mark_used, false);

    List<TransactionBridge> spendable = client.getAllSpendable();

    Transaction tx = TransactionUtil.makeTransaction(wallet_database, spendable, out_list, fee, change);

    if (broadcast)
    {
      client.sendOrException(tx);
    }

    return tx;

    
  }

}

