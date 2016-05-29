package thankyou;

import static spark.Spark.get;
import static spark.Spark.post;

import org.bitcoin.protocols.payments.Protos;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;

import java.util.HashMap;

public class Server {

    public static void main(String[] args) throws Exception {

        Treasury treasury = new Treasury("org.bitcoin.regtest");
        treasury.start();

        Object obj = JSONValue.parse(args[0]);
        JSONArray jsonRecipients = (JSONArray)obj;
        JSONObject[] recipients = new JSONObject[jsonRecipients.size()];

        for(int i=0; i<jsonRecipients.size(); i++) {
            recipients[i] = (JSONObject)jsonRecipients.get(i);
        }

        treasury.setRecipients(recipients);

        System.out.println(treasury.freshReceiveAddress());

        MustacheTemplateEngine mustache = new MustacheTemplateEngine();

        get("/", (req, res) -> { res.redirect("/thankyou"); return null; } );

        get("/thankyou", (req, res) -> {
            res.status(200);
            res.type("text/html");

            HashMap data = new HashMap();
            data.put("receivingAddress", treasury.freshReceiveAddress());
            data.put("recipients", recipients);
            data.put("user", System.getenv("USER"));

            return mustache.render(new ModelAndView(data, "thankyou.mustache"));
        });

        post("/payment", (req, res) -> {
            return "to be implemented";
        });
        get("/request", (req, res) -> {
            Protos.Payment paymentMessage = Protos.Payment.newBuilder().build();
            Protos.PaymentACK paymentAck = PaymentProtocol.createPaymentAck(paymentMessage, "thanks");
            return paymentAck;
        });
    }
}
