package com.itsinbox.smartbox.logic;

import com.itsinbox.smartbox.logic.SmartCardLogic;
import com.itsinbox.smartbox.model.SmartCard;
import com.itsinbox.smartbox.utils.Utils;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.keyinfo.X509IssuerSerial;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.DigestMethodParameterSpec;
import javax.xml.crypto.dsig.spec.SignatureMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class SigningLogic {

   private String chosenAlias;
   private SmartCard card = null;
   private String xml;
   private String personalId;
   private String signatureStr;
   private String firstName;
   private String lastName;


   public String getChosenAlias() {
      return this.chosenAlias;
   }

   public void setChosenAlias(String chosenAlias) {
      this.chosenAlias = chosenAlias;
   }

   public String getXml() {
      return this.xml;
   }

   public void setXml(String xml) {
      this.xml = xml;
   }

   public SmartCard getCard() {
      return this.card;
   }

   public void setCard(SmartCard card) {
      this.card = card;
   }

   public void setPersonalId(String personalId) {
      this.personalId = personalId;
   }

   public String getSignatureStr() {
      return this.signatureStr;
   }

   public void setSignatureStr(String signatureStr) {
      this.signatureStr = signatureStr;
   }

   public void setFirstName(String firstName) {
      this.firstName = firstName;
   }

   public void setLastName(String lastName) {
      this.lastName = lastName;
   }

   private SigningLogic.PrivateKeyAndCertChain getPrivateKeyAndCertChain(KeyStore aKeyStore) {
      String validAlias = this.chosenAlias;
      if(validAlias == null) {
         validAlias = SmartCardLogic.findAlias(aKeyStore);
      }

      this.getCertificateInfo(validAlias);

      try {
         if(validAlias == null) {
            return null;
         } else {
            Certificate[] ex = aKeyStore.getCertificateChain(validAlias);
            PrivateKey privateKey = (PrivateKey)aKeyStore.getKey(validAlias, (char[])null);
            SigningLogic.PrivateKeyAndCertChain result = new SigningLogic.PrivateKeyAndCertChain();
            result.mPrivateKey = privateKey;
            result.mCertificationChain = ex;

            for(int i = 0; i < ex.length; ++i) {
               X509Certificate chainMember = (X509Certificate)ex[i];
               boolean[] keyUsage = chainMember.getKeyUsage();
               Utils.logMessage("Chain member: " + i);
               if(keyUsage[0]) {
                  Utils.logMessage("digitalSignature");
               }

               if(keyUsage[1]) {
                  Utils.logMessage("nonRepudiation");
               }

               if(keyUsage[2]) {
                  Utils.logMessage("keyEncypherment");
               }

               if(keyUsage[3]) {
                  Utils.logMessage("dataEncypherment");
               }

               if(keyUsage[4]) {
                  Utils.logMessage("keyAgreement");
               }

               if(keyUsage[5]) {
                  Utils.logMessage("keyCertSign");
               }

               if(keyUsage[6]) {
                  Utils.logMessage("cRLSign");
               }

               if(keyUsage[7]) {
                  Utils.logMessage("encipherOnly");
               }

               if(keyUsage[8]) {
                  Utils.logMessage("decipherOnly");
               }

               if(keyUsage[0] || keyUsage[1]) {
                  result.mCertificate = chainMember;
               }
            }

            return result;
         }
      } catch (UnrecoverableKeyException var9) {
         Utils.logMessage("Error while getting key and certificate chain: " + var9.getMessage());
      } catch (NoSuchAlgorithmException e) {
         e.printStackTrace();
      } catch (KeyStoreException e) {
         e.printStackTrace();
      }
      return null;
   }

   public boolean signXml(KeyStore keyStore) {
      SigningLogic.PrivateKeyAndCertChain privateKeyAndCertChain = this.getPrivateKeyAndCertChain(keyStore);
      X509Certificate firstInChain = privateKeyAndCertChain.mCertificate;
      if(firstInChain == null) {
         firstInChain = (X509Certificate)privateKeyAndCertChain.mCertificationChain[0];
      }

      KeyPair kp = new KeyPair(firstInChain.getPublicKey(), privateKeyAndCertChain.mPrivateKey);
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);

      DocumentBuilder builder;
      try {
         builder = dbf.newDocumentBuilder();
      } catch (ParserConfigurationException var36) {
         Utils.logMessage("Error while building document for signing: " + var36.getMessage());
         return false;
      }

      Document doc = null;
      try {
         doc = builder.parse((new URL(this.getXml() + "?jmbg=" + this.personalId)).openStream());
      } catch (IOException var35) {
         Utils.logMessage("Error while parsing document for signing: " + var35.getMessage());
         return false;
      } catch (SAXException e) {
         e.printStackTrace();
      }

      Element docroot = doc.getDocumentElement();
      Element sigsElement = (Element)docroot.getElementsByTagName("signatures").item(0);
      DOMSignContext dsc = new DOMSignContext(kp.getPrivate(), sigsElement);
      Element refElement = (Element)docroot.getElementsByTagName("deklaracijaZaglavlje").item(0);
      refElement.setIdAttributeNode(refElement.getAttributeNodeNS((String)null, "id"), true);
      String refId = refElement.getAttribute("id");

      try {
         XMLSignatureFactory ex = XMLSignatureFactory.getInstance("DOM");
         ArrayList transformList = new ArrayList();
         Object tps = null;
         ex.newTransform("http://www.w3.org/2000/09/xmldsig#enveloped-signature", (TransformParameterSpec)tps);
         Transform c14NTransform = ex.newTransform("http://www.w3.org/2001/10/xml-exc-c14n#", (TransformParameterSpec)tps);
         transformList.add(c14NTransform);
         Reference ref = ex.newReference("#" + refId, ex.newDigestMethod("http://www.w3.org/2000/09/xmldsig#sha1", (DigestMethodParameterSpec)null), transformList, (String)null, (String)null);
         SignedInfo si = ex.newSignedInfo(ex.newCanonicalizationMethod("http://www.w3.org/2001/10/xml-exc-c14n#", (C14NMethodParameterSpec)null), ex.newSignatureMethod("http://www.w3.org/2000/09/xmldsig#rsa-sha1", (SignatureMethodParameterSpec)null), Collections.singletonList(ref));
         KeyInfoFactory kif = ex.getKeyInfoFactory();
         ArrayList x509 = new ArrayList();
         x509.add(firstInChain);
         X509IssuerSerial x509IssuerSerial = kif.newX509IssuerSerial(firstInChain.getIssuerDN().getName(), firstInChain.getSerialNumber());
         x509.add(x509IssuerSerial);
         x509.add(firstInChain.getSubjectX500Principal().toString());
         X509Data x509Data = kif.newX509Data(x509);
         kif.newKeyValue(kp.getPublic());
         ArrayList items = new ArrayList();
         items.add(x509Data);
         KeyInfo ki = kif.newKeyInfo(items);
         XMLSignature signature = ex.newXMLSignature(si, ki);
         signature.sign(dsc);

         try {
            StringWriter ex1 = new StringWriter();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(doc), new StreamResult(ex1));
            String strFileContent = ex1.toString();

            try {
               this.setSignatureStr(URLEncoder.encode(strFileContent, "UTF-8"));
            } catch (UnsupportedEncodingException var32) {
               Utils.logMessage("Error while writing signature results: " + var32.getMessage());
            }

            Utils.logMessage("SIGNATURE: " + this.getSignatureStr());
            return true;
         } catch (TransformerException var33) {
            Utils.logMessage("Error while writing signature results: " + var33.getMessage());
            return false;
         }
      } catch (XMLSignatureException var34) {
         Utils.logMessage("Error while signing XML: " + var34.getMessage());
      } catch (KeyException e) {
         e.printStackTrace();
      } catch (MarshalException e) {
         e.printStackTrace();
      } catch (NoSuchAlgorithmException e) {
         e.printStackTrace();
      } catch (InvalidAlgorithmParameterException e) {
         e.printStackTrace();
      }
      return false;
   }

   private String getCertificateInfo(String alias) {
      StringBuilder text = new StringBuilder();

      try {
         if(this.getCard() != null) {
            StringBuilder ex = new StringBuilder();
            Certificate[] chain = this.getCard().getKeyStore().getCertificateChain(alias);
            if(chain.length > 0) {
               X509Certificate firstInChain = (X509Certificate)chain[0];
               String dn = firstInChain.getSubjectX500Principal().toString();

               try {
                  LdapName ldapDN = new LdapName(dn);
                  Iterator<Rdn> ex1 = ldapDN.getRdns().iterator();

                  while(ex1.hasNext()) {
                     Rdn rdn = (Rdn)ex1.next();
                     String rdnType = rdn.getType();
                     if(rdnType.equals("GIVENNAME")) {
                        String givenName = rdn.getValue().toString();
                        if(givenName != null) {
                           this.setFirstName(givenName);
                        }
                     } else if (rdnType.equals("SURNAME")) {
                        String lastName = rdn.getValue().toString();
                        if(lastName != null) {
                           this.setLastName(lastName);
                        }
                     }
                  }
               } catch (InvalidNameException var12) {
                  Utils.logMessage("Error while getting certificate info: " + var12);
               }

               ex.append(this.firstName).append(" ").append(this.lastName).append(" [").append(this.personalId).append("]");
               text.append(ex.toString());
            }
         } else {
            text.append("Not a key entry!");
         }
      } catch (KeyStoreException var13) {
         Utils.logMessage("Error while getting certificate info: " + var13);
      }

      return text.toString();
   }

   public boolean signXmlForLogin(KeyStore keyStore) throws IOException {
      SigningLogic.PrivateKeyAndCertChain privateKeyAndCertChain = this.getPrivateKeyAndCertChain(keyStore);
      X509Certificate firstInChain = privateKeyAndCertChain.mCertificate;
      if(firstInChain == null) {
         firstInChain = (X509Certificate)privateKeyAndCertChain.mCertificationChain[0];
      }

      KeyPair kp = new KeyPair(firstInChain.getPublicKey(), privateKeyAndCertChain.mPrivateKey);
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);

      DocumentBuilder builder;
      try {
         builder = dbf.newDocumentBuilder();
      } catch (ParserConfigurationException var36) {
         Utils.logMessage("Error while building document for signing: " + var36.getMessage());
         return false;
      }

      Document doc;
      try {
         String docroot = this.getXml() + "?jmbg=" + this.personalId;
         doc = builder.parse((new URL(docroot)).openStream());
      } catch (SAXException var35) {
         Utils.logMessage("Error while parsing document for signing: " + var35.getMessage());
         return false;
      }

      Element docroot1 = doc.getDocumentElement();
      Element sigsElement = (Element)docroot1.getElementsByTagName("signatures").item(0);
      DOMSignContext dsc = new DOMSignContext(kp.getPrivate(), sigsElement);
      Element refElement = (Element)docroot1.getElementsByTagName("deklaracijaZaglavlje").item(0);
      refElement.setIdAttributeNode(refElement.getAttributeNodeNS((String)null, "id"), true);
      String refId = refElement.getAttribute("id");

      try {
         XMLSignatureFactory ex = XMLSignatureFactory.getInstance("DOM");
         ArrayList transformList = new ArrayList();
         Object tps = null;
         ex.newTransform("http://www.w3.org/2000/09/xmldsig#enveloped-signature", (TransformParameterSpec)tps);
         Transform c14NTransform = ex.newTransform("http://www.w3.org/2001/10/xml-exc-c14n#", (TransformParameterSpec)tps);
         transformList.add(c14NTransform);
         Reference ref = ex.newReference("#" + refId, ex.newDigestMethod("http://www.w3.org/2000/09/xmldsig#sha1", (DigestMethodParameterSpec)null), transformList, (String)null, (String)null);
         SignedInfo si = ex.newSignedInfo(ex.newCanonicalizationMethod("http://www.w3.org/2001/10/xml-exc-c14n#", (C14NMethodParameterSpec)null), ex.newSignatureMethod("http://www.w3.org/2000/09/xmldsig#rsa-sha1", (SignatureMethodParameterSpec)null), Collections.singletonList(ref));
         KeyInfoFactory kif = ex.getKeyInfoFactory();
         ArrayList x509 = new ArrayList();
         x509.add(firstInChain);
         X509IssuerSerial x509IssuerSerial = kif.newX509IssuerSerial(firstInChain.getIssuerDN().getName(), firstInChain.getSerialNumber());
         x509.add(x509IssuerSerial);
         x509.add(firstInChain.getSubjectX500Principal().toString());
         X509Data x509Data = kif.newX509Data(x509);
         KeyValue kv = kif.newKeyValue(kp.getPublic());
         ArrayList items = new ArrayList();
         items.add(x509Data);
         items.add(kv);
         KeyInfo ki = kif.newKeyInfo(items);
         XMLSignature signature = ex.newXMLSignature(si, ki);
         signature.sign(dsc);

         try {
            StringWriter ex1 = new StringWriter();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(doc), new StreamResult(ex1));
            String strFileContent = ex1.toString();

            try {
               this.setSignatureStr(URLEncoder.encode(strFileContent, "UTF-8"));
            } catch (UnsupportedEncodingException var32) {
               Utils.logMessage("Error while writing signature results: " + var32);
            }

            Utils.logMessage("SIGNATURE: " + this.getSignatureStr());
            return true;
         } catch (TransformerException var33) {
            Utils.logMessage("Error while writing signature results: " + var33.getMessage());
            return false;
         }
      } catch (XMLSignatureException var34) {
         Utils.logMessage("Error while signing XML for login: " + var34);
      } catch (KeyException e) {
         e.printStackTrace();
      } catch (MarshalException e) {
         e.printStackTrace();
      } catch (NoSuchAlgorithmException e) {
         e.printStackTrace();
      } catch (InvalidAlgorithmParameterException e) {
         e.printStackTrace();
      }
      return false;
   }

   static class PrivateKeyAndCertChain {

      public PrivateKey mPrivateKey;
      public Certificate[] mCertificationChain;
      public X509Certificate mCertificate;


   }
}
