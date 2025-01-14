package hlf.java.rest.client.service.impl;

import static hlf.java.rest.client.model.ChaincodeOperationsType.approve;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import hlf.java.rest.client.exception.ErrorCode;
import hlf.java.rest.client.exception.ServiceException;
import hlf.java.rest.client.model.ChaincodeOperations;
import hlf.java.rest.client.model.ChaincodeOperationsType;
import hlf.java.rest.client.service.ChaincodeOperationsService;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Network;
import org.hyperledger.fabric.gateway.impl.identity.X509IdentityProvider;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.LifecycleApproveChaincodeDefinitionForMyOrgProposalResponse;
import org.hyperledger.fabric.sdk.LifecycleApproveChaincodeDefinitionForMyOrgRequest;
import org.hyperledger.fabric.sdk.LifecycleCommitChaincodeDefinitionProposalResponse;
import org.hyperledger.fabric.sdk.LifecycleCommitChaincodeDefinitionRequest;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChaincodeOperationsServiceImpl implements ChaincodeOperationsService {

  @Autowired private Gateway gateway;

  @Override
  public String performChaincodeOperation(
      String networkName,
      ChaincodeOperations chaincodeOperationsModel,
      ChaincodeOperationsType operationsType) {

    validateChaincodeOperationsInput(chaincodeOperationsModel, operationsType);
    HFClient hfClient = HFClient.createNewInstance();
    try {
      hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
      X509IdentityProvider.INSTANCE.setUserContext(
          hfClient, gateway.getIdentity(), "hlf-connector");

      Network network = gateway.getNetwork(networkName);
      Channel channel = network.getChannel();

      switch (operationsType) {
        case approve:
          {
            return approveChaincode(hfClient, channel, chaincodeOperationsModel);
          }
        case commit:
          {
            return commitChaincode(hfClient, channel, chaincodeOperationsModel);
          }
        default:
          {
            throw new ServiceException(
                ErrorCode.NOT_SUPPORTED, "The passed chaincode operation not supported.");
          }
      }

    } catch (CryptoException
        | InvalidArgumentException
        | IllegalAccessException
        | InstantiationException
        | ClassNotFoundException
        | NoSuchMethodException
        | InvocationTargetException e) {
      throw new ServiceException(ErrorCode.HYPERLEDGER_FABRIC_CONNECTION_ERROR, e.getMessage());
    }
  }

  private String approveChaincode(
      HFClient hfClient, Channel channel, ChaincodeOperations chaincodeOperationsModel) {

    Collection<Peer> peers = channel.getPeers();
    try {
      LifecycleApproveChaincodeDefinitionForMyOrgRequest
          lifecycleApproveChaincodeDefinitionForMyOrgRequest =
              hfClient.newLifecycleApproveChaincodeDefinitionForMyOrgRequest();
      lifecycleApproveChaincodeDefinitionForMyOrgRequest.setSequence(
          chaincodeOperationsModel.getSequence());
      lifecycleApproveChaincodeDefinitionForMyOrgRequest.setChaincodeName(
          chaincodeOperationsModel.getChaincodeName());
      lifecycleApproveChaincodeDefinitionForMyOrgRequest.setChaincodeVersion(
          chaincodeOperationsModel.getChaincodeVersion());
      lifecycleApproveChaincodeDefinitionForMyOrgRequest.setInitRequired(
          chaincodeOperationsModel.getInitRequired());

      // TODO: Add chaincodeCollectionConfiguration and chaincodeEndorsementPolicy

      lifecycleApproveChaincodeDefinitionForMyOrgRequest.setPackageId(
          chaincodeOperationsModel.getChaincodePackageID());

      Collection<LifecycleApproveChaincodeDefinitionForMyOrgProposalResponse>
          lifecycleApproveChaincodeDefinitionForMyOrgProposalResponse =
              channel.sendLifecycleApproveChaincodeDefinitionForMyOrgProposal(
                  lifecycleApproveChaincodeDefinitionForMyOrgRequest, peers);

      CompletableFuture<BlockEvent.TransactionEvent> transactionEventCompletableFuture =
          channel.sendTransaction(lifecycleApproveChaincodeDefinitionForMyOrgProposalResponse);

      // Making the sendTransaction call synchronous
      BlockEvent.TransactionEvent transactionEvent = transactionEventCompletableFuture.join();
      return transactionEvent.getTransactionID();
    } catch (InvalidArgumentException e) {
      log.error(
          "Action Failed: A problem occurred while creating request for chaincode approval with InvalidArgumentException.",
          e);
      throw new ServiceException(
          ErrorCode.HYPERLEDGER_FABRIC_CHAINCODE_OPERATIONS_REQUEST_REJECTION, e.getMessage(), e);
    } catch (ProposalException e) {
      log.error(
          "Action Failed: A problem occurred while sending transaction for chaincode approval.", e);
      throw new ServiceException(
          ErrorCode.HYPERLEDGER_FABRIC_CHAINCODE_OPERATIONS_REQUEST_REJECTION, e.getMessage(), e);
    }
  }

  private String commitChaincode(
      HFClient hfClient, Channel channel, ChaincodeOperations chaincodeOperationsModel) {

    Collection<Peer> peers = channel.getPeers();
    try {
      LifecycleCommitChaincodeDefinitionRequest lifecycleCommitChaincodeDefinitionRequest =
          hfClient.newLifecycleCommitChaincodeDefinitionRequest();

      lifecycleCommitChaincodeDefinitionRequest.setSequence(chaincodeOperationsModel.getSequence());
      lifecycleCommitChaincodeDefinitionRequest.setChaincodeName(
          chaincodeOperationsModel.getChaincodeName());
      lifecycleCommitChaincodeDefinitionRequest.setChaincodeVersion(
          chaincodeOperationsModel.getChaincodeVersion());

      // TODO: Add chaincodeCollectionConfiguration and chaincodeEndorsementPolicy

      lifecycleCommitChaincodeDefinitionRequest.setInitRequired(
          chaincodeOperationsModel.getInitRequired());

      Collection<LifecycleCommitChaincodeDefinitionProposalResponse>
          lifecycleCommitChaincodeDefinitionProposalResponses =
              channel.sendLifecycleCommitChaincodeDefinitionProposal(
                  lifecycleCommitChaincodeDefinitionRequest, peers);

      CompletableFuture<BlockEvent.TransactionEvent> transactionEventCompletableFuture =
          channel.sendTransaction(lifecycleCommitChaincodeDefinitionProposalResponses);

      // Making the sendTransaction call synchronous
      BlockEvent.TransactionEvent transactionEvent = transactionEventCompletableFuture.join();
      return transactionEvent.getTransactionID();

    } catch (InvalidArgumentException e) {
      log.error(
          "Action Failed: A problem occurred while creating request for chaincode commit with InvalidArgumentException.",
          e);
      throw new ServiceException(
          ErrorCode.HYPERLEDGER_FABRIC_CHAINCODE_OPERATIONS_REQUEST_REJECTION, e.getMessage(), e);
    } catch (ProposalException e) {
      log.error(
          "Action Failed: A problem occurred while sending transaction for chaincode commit.", e);
      throw new ServiceException(
          ErrorCode.HYPERLEDGER_FABRIC_CHAINCODE_OPERATIONS_REQUEST_REJECTION, e.getMessage(), e);
    }
  }

  private void validateChaincodeOperationsInput(
      ChaincodeOperations chaincodeOperations, ChaincodeOperationsType operationsType) {
    if (isEmpty(chaincodeOperations.getChaincodeName())
        || isEmpty(chaincodeOperations.getChaincodeVersion())
        || isNull(chaincodeOperations.getSequence())
        || isNull(chaincodeOperations.getInitRequired())
        || (operationsType.equals(approve)
            && isEmpty(chaincodeOperations.getChaincodePackageID()))) {
      throw new ServiceException(
          ErrorCode.VALIDATION_FAILED,
          "Chaincode operations data passed is incorrect or not supported.");
    }
  }
}
