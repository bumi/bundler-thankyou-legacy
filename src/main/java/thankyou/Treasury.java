package thankyou;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.SPVBlockStore;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class Treasury {
    public static Treasury instance;
    private boolean useLocalhost;
    public NetworkParameters params;
    public static WalletAppKit kit;
    static Logger logger = LoggerFactory.getLogger(Treasury.class.getName());
    public String[] recipients;

    public Treasury(String environment) throws Exception {
        this(environment, new File("."));
    }

    public Treasury(String environment, File directory) throws Exception {
        this(environment, directory, false);
    }

    public Treasury(String environment, File directory, boolean useLocalhost) throws Exception {
        this.params = this.paramsForEnvironment(environment);
        kit = new WalletAppKit(params, directory, "thankyou-" + environment);
        this.useLocalhost = useLocalhost;
    }


    public void start() throws Exception {
        if (this.params.getId().equals(NetworkParameters.ID_REGTEST) || this.useLocalhost) {
            kit.connectToLocalHost();
        }

        DownloadProgressTracker bListener = new DownloadProgressTracker() {
            @Override
            public void progress(double pct, int blocksSoFar, Date date) {

            }
            @Override
            public void startDownload(int blocks) {
                logger.info("Syncing with the Bitcoin network, this might take a few minutes.");
            }
            @Override
            public void doneDownload() {
                logger.info("OK, done synicng. Thanks for waiting.");
            }
        };

        kit.startAsync();
        kit.awaitRunning();
        kit.wallet().addCoinsReceivedEventListener(new WalletListener(this.recipients));
        kit.wallet().allowSpendingUnconfirmedTransactions();
    }

    public String freshReceiveAddress() {
        return kit.wallet().freshReceiveAddress().toString();
    }

    public void setRecipients(JSONObject[] recipients) {
        String[] r = new String[recipients.length];
        for(int i = 0; i < recipients.length; i++) {
            r[i] = (String)recipients[i].get("address");
        }
        this.recipients = r;
    }
    public String[] getRecipients() {
        return this.recipients;
    }

    private NetworkParameters paramsForEnvironment(String networkId) {
        if (networkId == null) {
            networkId = NetworkParameters.ID_TESTNET;
        }
        return NetworkParameters.fromID(networkId);
    }

    static class WalletListener implements WalletCoinsReceivedEventListener {

        String[] recipients;

        public WalletListener(String[] recipients) {
            this.recipients = recipients;
        }

        @Override
        public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            if(!tx.getValueSentFromMe(wallet).isZero()) { return; } // hack because we get some change right now. see below.

            NetworkParameters params = wallet.getParams();
            Coin value = tx.getValueSentToMe(wallet);
            logger.info("received " + value.toFriendlyString() + " in transaction " + tx.getHashAsString() + " forwarding now");


            List<TransactionOutput> candidates = wallet.calculateAllSpendCandidates();
            CoinSelection selection = wallet.getCoinSelector().select(params.getMaxMoney(), candidates);

            // TODO: proper fee calculation per KB
            // right now this does not really work, because right now we do not have an easy way to send all the money to multiple outputs.
            // bitcoinj only has an option to empty the wallet to one output.
            // that means we will get some change... but we do not want that.
            // TODO: manually create the transaction
            Coin amountToSend = selection.valueGathered.subtract(Transaction.DEFAULT_TX_FEE); //value.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
            Coin recipientAmount = amountToSend.divide(recipients.length);

            Transaction sendTx = new Transaction(params);

            for(String address : this.recipients){
                sendTx.addOutput(recipientAmount, Address.fromBase58(params, address));
            }

            SendRequest req = SendRequest.forTx(sendTx);


            try {
                Wallet.SendResult result = wallet.sendCoins(req);
                logger.info("transaction sent" + result.tx.getHash().toString());
            } catch (InsufficientMoneyException e) {
                System.out.println("not enough money");
            }
        }
    }
}
