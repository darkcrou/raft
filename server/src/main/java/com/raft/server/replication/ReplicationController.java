package com.raft.server.replication;

import com.raft.server.context.Context;
import com.raft.server.context.Peer;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;


@RestController
@RequestMapping(value = "/replication",produces = {MediaType.APPLICATION_JSON_VALUE})
@Api(tags="Replication")
@RequiredArgsConstructor
class ReplicationController {

    private final ReplicationService replicationService;

    @PostMapping
    @ApiOperation(value = "Get current peer state")
    public AnswerAppendDTO getCurrentPeerState(@Valid @RequestBody RequestAppendDTO requestAppendDTO,
                                               BindingResult bindingResult) throws BindException {

        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }
        return replicationService.append(requestAppendDTO);
    }




}
